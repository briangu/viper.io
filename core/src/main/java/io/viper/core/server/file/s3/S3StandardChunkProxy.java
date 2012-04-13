package io.viper.core.server.file.s3;


import com.amazon.s3.QueryStringAuthGenerator;
import com.amazon.s3.S3Object;
import com.amazon.s3.Utils;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.jboss.netty.bootstrap.ClientBootstrap;
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
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import io.viper.core.server.file.HttpChunkProxyEventListener;
import io.viper.core.server.file.HttpChunkRelayProxy;


public class S3StandardChunkProxy implements HttpChunkRelayProxy
{
  private final QueryStringAuthGenerator _s3AuthGenerator;
  private final String _bucketName;
  private String _bucketKey;

  private Map<String, String> _objectMeta;
  private long _objectSize;

  private final ClientSocketChannelFactory _cf;
  private final URI _amazonHost;
  private volatile Channel _s3Channel;

  private enum State
  {
    init,
    connected,
    relay,
    complete,
    closed
  }

  private State _state = State.closed;
  private volatile HttpChunkProxyEventListener _listener;

  public S3StandardChunkProxy(String awsId, String awsKey, String bucketName)
    throws URISyntaxException
  {
    this(new QueryStringAuthGenerator(awsId, awsKey),
         bucketName,
         new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool()),
         new URI(Utils.DEFAULT_HOST));
  }

  public S3StandardChunkProxy(QueryStringAuthGenerator s3AuthGenerator,
                              String bucketName,
                              ClientSocketChannelFactory cf,
                              URI amazonHost)
  {
    _s3AuthGenerator = s3AuthGenerator;
    _bucketName = bucketName;
    _cf = cf;
    _amazonHost = amazonHost;
  }

  private void connect()
  {
    ClientBootstrap cb = new ClientBootstrap(_cf);
    cb.getPipeline().addLast("encoder", new HttpRequestEncoder());
    cb.getPipeline().addLast("decoder", new HttpResponseDecoder());
    cb.getPipeline().addLast("handler", new S3ResponseHandler(_listener));
    ChannelFuture f = cb.connect(new InetSocketAddress(_amazonHost.getHost(), _amazonHost.getPort()));

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
          _listener.onProxyConnected();
          _listener.onProxyWriteReady();
        }
        else
        {
          _listener.onProxyError();
          closeS3Channel();
        }
      }
    });
  }

  @Override
  public boolean isRelaying()
  {
    return _state == State.relay;
  }

  @Override
  public void init(
    HttpChunkProxyEventListener listener,
    String objectName,
    Map<String, String> meta,
    long objectSize)
      throws Exception
  {
    _state = State.init;
    _listener = listener;
    _bucketKey = objectName;
    _objectMeta = meta;
    _objectSize = objectSize;

    _listener.onProxyWritePaused();

    connect();
  }

  private HttpRequest buildRequest(HttpChunk chunk)
  {
    Map<String, List<String>> headers = new HashMap<String, List<String>>();
//    headers.put("x-amz-acl", Arrays.asList("public-read"));
    headers.put("x-amz-meta-filename", Arrays.asList(_objectMeta.get("filename")));
    headers.put(HttpHeaders.Names.CONTENT_LENGTH, Arrays.asList(Long.toString(_objectSize)));
    headers.put(HttpHeaders.Names.CONTENT_TYPE, Arrays.asList(_objectMeta.get(HttpHeaders.Names.CONTENT_TYPE)));
    S3Object object = new S3Object(null, new HashMap<String, List<String>>());
    String uri = _s3AuthGenerator.put(_bucketName, _bucketKey, object, headers);

    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, uri);
    for (String headerKey : headers.keySet())
    {
      request.setHeader(headerKey, headers.get(headerKey).get(0));
    }
    request.setContent(chunk.getContent());

    return request;
  }

  @Override
  public void writeChunk(final HttpChunk chunk)
  {
    if (_s3Channel == null) return;

    if (_state.equals(State.connected))
    {
      HttpRequest request = buildRequest(chunk);

      _listener.onProxyWritePaused();

      ChannelFuture f = _s3Channel.write(request);
      f.addListener(new ChannelFutureListener()
      {
        @Override
        public void operationComplete(ChannelFuture channelFuture)
          throws Exception
        {
          if (channelFuture.isSuccess())
          {
            if (chunk.isLast())
            {
              _state = State.complete;
              _listener.onProxyCompleted();
            }
            else
            {
              _state = State.relay;
              _listener.onProxyWriteReady();
            }
          }
          else
          {
            _listener.onProxyError();
            closeS3Channel();
          }
        }
      });
    }
    else if (_state.equals(State.relay))
    {
      ChannelFuture f = _s3Channel.write(chunk.getContent());
      f.addListener(new ChannelFutureListener()
      {
        @Override
        public void operationComplete(ChannelFuture future)
          throws Exception
        {
          if (future.isSuccess())
          {
            if (chunk.isLast())
            {
              _state = State.complete;
              _listener.onProxyCompleted();
            }
          }
          else
          {
            _listener.onProxyError();
            closeS3Channel();
          }
        }
      });
    }

    if (_s3Channel != null)
    {
      if (!_s3Channel.isWritable())
      {
        _listener.onProxyWritePaused();
      }
    }
  }

  @Override
  public void abort()
  {
    closeS3Channel();
  }

  private class S3ResponseHandler extends SimpleChannelUpstreamHandler
  {

    private volatile HttpChunkProxyEventListener _listener;

    S3ResponseHandler(HttpChunkProxyEventListener listener)
    {
      _listener = listener;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e)
      throws Exception
    {
      Object obj = e.getMessage();
      if (!(obj instanceof HttpResponse))
      {
        return;
      }

      HttpResponse m = (HttpResponse)obj;

      if (!m.getStatus().equals(HttpResponseStatus.OK))
      {
        closeS3Channel();
        _listener.onProxyError();
      }
    }

    @Override
    public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e)
      throws Exception
    {
      // If _s3Channel is not saturated anymore, continue accepting
      // the incoming traffic from the inboundChannel.
      if (e.getChannel().isWritable())
      {
        _listener.onProxyWriteReady();
      }
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
      throws Exception
    {
      closeOnFlush(_s3Channel);
      closeS3Channel();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
      throws Exception
    {
      e.getCause().printStackTrace();
      closeOnFlush(e.getChannel());
      closeS3Channel();
      _listener.onProxyError();
    }
  }

  void closeS3Channel()
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
    if (ch == null) return;

    if (ch.isConnected())
    {
      ch.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }
  }
}

