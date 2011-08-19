package viper.net.server.chunkproxy.s3;


import com.amazon.s3.QueryStringAuthGenerator;
import com.amazon.s3.S3Object;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.rmi.server.UID;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.activation.MimetypesFileTypeMap;
import javax.swing.plaf.UIResource;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.jboss.netty.handler.codec.rtsp.RtspRequestEncoder;
import org.jboss.netty.handler.codec.rtsp.RtspResponseDecoder;
import org.jboss.netty.util.CharsetUtil;
import viper.net.common.HttpResponseLoggingHandler;
import viper.net.server.CachableHttpResponse;
import viper.net.server.Util;
import viper.net.server.chunkproxy.HttpChunkProxyEventListener;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpMethod.GET;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;


public class S3StaticFileServerHandler extends SimpleChannelUpstreamHandler
{
  private final QueryStringAuthGenerator _s3AuthGenerator;
  private final String _bucketName;

  private String _prefixPath;

  private final ClientSocketChannelFactory _cf;
  private final String _remoteHost;
  private final int _remotePort;
  private Channel _s3Channel;

  private HttpRequest _request;
  private Channel _destChannel;
  private String _bucketKey;

  final Object _trafficLock = new Object();

  private enum State
  {
    init,
    connected,
    relay,
    complete,
    closed
  }

  private State _state = State.closed;

  private static File _indexFile = null;

  // TODO: add support for index.htm

  public S3StaticFileServerHandler(
    QueryStringAuthGenerator s3AuthGenerator,
    String bucketName,
    ClientSocketChannelFactory cf,
    String remoteHost,
    int remotePort,
    String prefixPath)
  {
    _s3AuthGenerator = s3AuthGenerator;
    _bucketName = bucketName;
    _cf = cf;
    _remoteHost = remoteHost;
    _remotePort = remotePort;
    _prefixPath = prefixPath;
  }

  private void connect()
  {
    ClientBootstrap cb = new ClientBootstrap(_cf);
    cb.getPipeline().addLast("encoder", new HttpRequestEncoder());
    cb.getPipeline().addLast("decoder", new HttpResponseDecoder());
    cb.getPipeline().addLast("handler", new S3ResponseHandler(_destChannel));
    ChannelFuture f = cb.connect(new InetSocketAddress(_remoteHost, _remotePort));

    _s3Channel = f.getChannel();
    f.addListener(new ChannelFutureListener()
    {
      @Override
      public void operationComplete(ChannelFuture future)
        throws Exception
      {
        if (future.isSuccess())
        {
          _state = State.connected;

          Map<String, List<String>> headers = new HashMap<String, List<String>>();
          String uri = _s3AuthGenerator.get(_bucketName, _bucketKey, headers);

          HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);

          ChannelFuture q = _s3Channel.write(request);
          q.addListener(new ChannelFutureListener()
          {
            @Override
            public void operationComplete(ChannelFuture future)
              throws Exception
            {
              if (future.isSuccess())
              {
                _state = State.relay;
              }
              else
              {
//                sendError();
                closeS3Channel();
              }
            }
          });
        }
        else
        {
          closeS3Channel();
        }
      }
    });
  }

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
    throws Exception
  {
    Object obj = e.getMessage();
    if (!(obj instanceof HttpRequest))
    {
      ctx.sendUpstream(e);
      return;
    }

    _request = (HttpRequest) e.getMessage();

    String uri = _request.getUri();
    final String path = sanitizeUri(uri);
    if (path == null)
    {
      sendError(ctx, FORBIDDEN);
      return;
    }

    if (!uri.startsWith(_prefixPath))
    {
      ctx.sendUpstream(e);
      return;
    }

    if (_request.getMethod() != GET)
    {
      sendError(ctx, METHOD_NOT_ALLOWED);
      return;
    }

    _destChannel = e.getChannel();
    _bucketKey = uri.substring(uri.lastIndexOf("/")+1);

    _state = State.init;

    connect();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    throws Exception
  {
    Channel ch = e.getChannel();
    Throwable cause = e.getCause();
    if (cause instanceof TooLongFrameException)
    {
      sendError(ctx, BAD_REQUEST);
      return;
    }

    cause.printStackTrace();
    if (ch.isConnected())
    {
      sendError(ctx, INTERNAL_SERVER_ERROR);
    }

    if (_s3Channel != null)
    {
      _s3Channel.close();
      _s3Channel = null;
    }
  }

  private String sanitizeUri(String uri)
    throws URISyntaxException
  {
    // Decode the path.
    try
    {
      uri = URLDecoder.decode(uri, "UTF-8");
    }
    catch (UnsupportedEncodingException e)
    {
      try
      {
        uri = URLDecoder.decode(uri, "ISO-8859-1");
      }
      catch (UnsupportedEncodingException e1)
      {
        throw new Error();
      }
    }

    // Convert file separators.
    uri = uri.replace(File.separatorChar, '/');

    // Simplistic dumb security check.
    // You will have to do something serious in the production environment.
    if (uri.contains(File.separator + ".")
          || uri.contains("." + File.separator)
          || uri.startsWith(".")
          || uri.endsWith("."))
    {
      return null;
    }

    QueryStringDecoder decoder = new QueryStringDecoder(uri);
    uri = decoder.getPath();

    return uri;
  }

  private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status)
  {
    HttpResponse response = new DefaultHttpResponse(HTTP_1_1, status);
    response.setHeader(CONTENT_TYPE, "text/plain; charset=UTF-8");
    response.setContent(ChannelBuffers.copiedBuffer("Failure: " + status.toString() + "\r\n", CharsetUtil.UTF_8));

    // Close the connection as soon as the error message is sent.
    ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
  }

  private class S3ResponseHandler extends SimpleChannelUpstreamHandler
  {

    private Channel _destChannel;

    S3ResponseHandler(Channel destChannel)
    {
      _destChannel = destChannel;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e)
      throws Exception
    {
      if (!_state.equals(State.relay))
      {
        return;
      }

      Object obj = e.getMessage();
      if (obj instanceof HttpResponse)
      {
        HttpResponse m = (HttpResponse)obj;

        if (m.getStatus().equals(HttpResponseStatus.OK))
        {
          String contentType = m.getHeader(Names.CONTENT_TYPE);
          long contentLength = Long.parseLong(m.getHeader(Names.CONTENT_LENGTH));

          DefaultHttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
          response.setHeader(Names.CONTENT_TYPE, contentType);
          setContentLength(response, contentLength);
          response.setContent(m.getContent());
          ChannelFuture f = _destChannel.write(response);
          f.addListener(new ChannelFutureListener()
          {
            @Override
            public void operationComplete(ChannelFuture future)
              throws Exception
            {
              if (!future.isSuccess())
              {
                // sendError();
                closeS3Channel();
              }
            }
          });
        }
        else
        {
          // sendError();
          closeS3Channel();
        }
      }
      else if (obj instanceof HttpChunk)
      {
        HttpChunk chunk = (HttpChunk)obj;
        ChannelFuture f = _destChannel.write(chunk);
        f.addListener(new ChannelFutureListener()
        {
          @Override
          public void operationComplete(ChannelFuture future)
            throws Exception
          {
            if (!future.isSuccess())
            {
              closeS3Channel();
            }
          }
        });

        if (!chunk.isLast())
        {
          if (!_destChannel.isWritable())
          {
            _s3Channel.setReadable(false);
          }
        }
      }
    }

//    @Override
    public void messageReceived2(ChannelHandlerContext ctx, final MessageEvent e)
      throws Exception
    {
      Object obj = e.getMessage();
      if (!(obj instanceof HttpResponse))
      {
        return;
      }

      HttpResponse m = (HttpResponse)obj;

      System.out.println("response in current state: " + _state);

      synchronized (_trafficLock)
      {
        if (_state.equals(State.init))
        {
          if (m.getStatus() == HttpResponseStatus.OK)
          {
            _state = State.connected;

            Map<String, List<String>> headers = new HashMap<String, List<String>>();
            String uri = _s3AuthGenerator.get(_bucketName, _bucketKey, headers);

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);

            _s3Channel.write(request);
          }
          else
          {
            closeS3Channel();
          }
        }
        else if (_state.equals(State.connected))
        {
          if (m.getStatus() == HttpResponseStatus.OK)
          {
            String contentType = m.getHeader(Names.CONTENT_TYPE);
            long contentLength = Long.parseLong(m.getHeader(Names.CONTENT_LENGTH));

            DefaultHttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
            response.setHeader(Names.CONTENT_TYPE, contentType);
            setContentLength(response, contentLength);
            response.setContent(m.getContent());
            _destChannel.write(response);

            _state = State.relay;
          }
          else
          {
            closeS3Channel();
          }
        }
        else if (_state.equals(State.relay))
        {
          if (m.getStatus() == HttpResponseStatus.OK)
          {
            _destChannel.write(m.getContent());

            if (!_destChannel.isWritable())
            {
              _s3Channel.setReadable(false);
            }
          }
          else
          {
            closeS3Channel();
          }
        }
        else
        {
          System.out.println("unhandled response @ " + _state);
        }
      }
    }

    @Override
    public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e)
      throws Exception
    {
      // If _s3Channel is not saturated anymore, continue accepting
      // the incoming traffic from the inboundChannel.
      synchronized (_trafficLock)
      {
        if (e.getChannel().isWritable())
        {
          _s3Channel.setReadable(true);
        }
      }
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
      throws Exception
    {
      closeOnFlush(_destChannel);
      closeS3Channel();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
      throws Exception
    {
      e.getCause().printStackTrace();
      closeOnFlush(e.getChannel());
      closeS3Channel();
    }
  }

  private void closeS3Channel()
  {
    _state = State.closed;
    if (_s3Channel != null)
    {
      _s3Channel.close();
      _s3Channel = null;
    }
  }

  /**
   * Closes the specified channel after all queued write requests are flushed.
   */
  static void closeOnFlush(Channel ch)
  {
    if (ch.isConnected())
    {
      ch.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }
  }
}
