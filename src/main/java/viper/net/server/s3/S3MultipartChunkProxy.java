package viper.net.server.s3;


import com.amazon.s3.QueryStringAuthGenerator;
import com.amazon.s3.S3Object;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.rtsp.RtspRequestEncoder;
import org.jboss.netty.handler.codec.rtsp.RtspResponseDecoder;
import viper.net.common.HttpResponseLoggingHandler;
import viper.net.server.HttpChunkRelayEventListener;
import viper.net.server.HttpChunkRelayProxy;


public class S3MultipartChunkProxy implements HttpChunkRelayProxy
{

  private final QueryStringAuthGenerator _s3AuthGenerator;
  private final String _bucketName;
  private volatile String _bucketKey;

  private List<String> _multipartEtags;

  private final ClientSocketChannelFactory _cf;
  private final String _remoteHost;
  private final int _remotePort;
  private volatile Channel _s3Channel;
  private int _chunkIndex;

  final Object _trafficLock = new Object();

  private enum State
  {
    init,
    relay,
    closed
  }

  private State _state = State.closed;
  private volatile HttpChunkRelayEventListener _listener;

  public S3MultipartChunkProxy(QueryStringAuthGenerator s3AuthGenerator,
                               String bucketName,
                               ClientSocketChannelFactory cf,
                               String remoteHost,
                               int remotePort)
  {
    _s3AuthGenerator = s3AuthGenerator;
    _bucketName = bucketName;
    _cf = cf;
    _remoteHost = remoteHost;
    _remotePort = remotePort;
  }

  private ChannelFuture connect()
  {
    ClientBootstrap cb = new ClientBootstrap(_cf);
    cb.getPipeline().addLast("log", new HttpResponseLoggingHandler());
    cb.getPipeline().addLast("encoder", new RtspRequestEncoder());
    cb.getPipeline().addLast("decoder", new RtspResponseDecoder());
    cb.getPipeline().addLast("handler", new S3ResponseHandler(_listener));
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
          _listener.onProxyReady();
        }
        else
        {
          _listener.onProxyError();
          _s3Channel.close();
        }
      }
    });

    return f;
  }

  @Override
  public boolean isRelaying()
  {
    return _state != State.closed;
  }

  @Override
  public void init(HttpChunkRelayEventListener listener, String objectName)
    throws Exception
  {
    _state = State.init;
    _listener = listener;
    _chunkIndex = 1;

    _listener.onProxyPaused();

    _bucketKey = objectName;
    _multipartEtags = new ArrayList<String>();

    ChannelFuture f = connect();
    f.addListener(new ChannelFutureListener()
    {
      @Override
      public void operationComplete(ChannelFuture channelFuture)
        throws Exception
      {
        String uri = _s3AuthGenerator.initMultipartUpload(_bucketName, _bucketKey);
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri);
        _s3Channel.write(request);
      }
    });
  }

  @Override
  public void appendChunk(HttpChunk chunk)
  {
    Map<String, String[]> meta = new HashMap<String, String[]>();
    S3Object object = new S3Object(null, meta);
    String uri = _s3AuthGenerator.uploadPart(_bucketName, _bucketKey, object);
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri);
    request.setContent(chunk.getContent());
    _s3Channel.write(request);

    if (!_s3Channel.isWritable())
    {
      _listener.onProxyPaused();
    }
  }

  @Override
  public void complete(HttpChunk chunk)
  {
    Map<String, String[]> meta = new HashMap<String, String[]>();
    S3Object object = new S3Object(null, meta);
    String uri = _s3AuthGenerator.completeMultipartUpload(_bucketName, _bucketKey, object);
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri);
    request.setContent(chunk.getContent());
    _s3Channel.write(request);
  }

  @Override
  public void abort()
  {
    String uri = _s3AuthGenerator.abortMultipartUpload(_bucketName, _bucketKey);
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri);
    _s3Channel.write(request);
  }

  private class S3ResponseHandler extends SimpleChannelUpstreamHandler
  {

    private volatile HttpChunkRelayEventListener _listener;

    S3ResponseHandler(HttpChunkRelayEventListener listener)
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

      synchronized (_trafficLock)
      {
        if (_state.equals(State.init))
        {
          if (m.getStatus() == HttpResponseStatus.OK)
          {
            _listener.onProxyReady();
            _state = State.relay;
          }
          else
          {
            _state = State.closed;
            _s3Channel.close();
            _listener.onProxyError();
          }
        }
        else if (_state.equals(State.relay))
        {
          if (m.getStatus() == HttpResponseStatus.OK)
          {
            // extract etag from result
            // String etag =
            // _multipartEtags.add(etag);
          }
          else
          {
            _state = State.closed;
            _s3Channel.close();
            _listener.onProxyError();
          }
        }
        else
        {
          // ignore
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
          _listener.onProxyReady();
        }
      }
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
      throws Exception
    {
      closeOnFlush(_s3Channel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
      throws Exception
    {
      e.getCause().printStackTrace();
      closeOnFlush(e.getChannel());
      _listener.onProxyError();
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

