package viper.net.server.s3;


import java.net.InetSocketAddress;
import java.util.List;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.rtsp.RtspRequestEncoder;
import org.jboss.netty.handler.codec.rtsp.RtspResponseDecoder;
import viper.net.server.HttpChunkRelayEventListener;
import viper.net.server.HttpChunkRelayProxy;


public class S3MultipartChunkProxy implements HttpChunkRelayProxy
{

  private final ClientSocketChannelFactory cf;
  private final String remoteHost;
  private final int remotePort;

  final Object trafficLock = new Object();

  HttpChunkRelayEventListener _listener;

  private volatile Channel inboundChannel;
  private volatile MessageEvent msg;

  private volatile Channel outboundChannel;

  private enum State
  {
    init,
    appending,
    closed
  }

  private State state = State.closed;

  public S3MultipartChunkProxy(ClientSocketChannelFactory cf,
                               String remoteHost,
                               int remotePort,
                               Channel inboundChannel,
                               MessageEvent msg)
  {
    this.cf = cf;
    this.remoteHost = remoteHost;
    this.remotePort = remotePort;
    this.inboundChannel = inboundChannel;
    this.msg = msg;
  }

  private ChannelFuture connect(MessageEvent e)
  {
    // Suspend incoming traffic until connected to the remote host.
    final Channel inboundChannel = e.getChannel();
    inboundChannel.setReadable(false);

    // Start the connection attempt.
    ClientBootstrap cb = new ClientBootstrap(cf);
    cb.getPipeline().addLast("encoder", new RtspRequestEncoder());
    cb.getPipeline().addLast("decoder", new RtspResponseDecoder());
    cb.getPipeline().addLast("handler", new OutboundHandler(e.getChannel()));
    ChannelFuture f = cb.connect(new InetSocketAddress(remoteHost, remotePort));

    outboundChannel = f.getChannel();
    f.addListener(new ChannelFutureListener()
    {
      @Override
      public void operationComplete(ChannelFuture future)
        throws Exception
      {
        if (future.isSuccess())
        {
          // Connection attempt succeeded:
          // Begin to accept incoming traffic.
//          inboundChannel.setReadable(true);
        }
        else
        {
          // Close the connection if the connection attempt has failed.
          inboundChannel.close();
        }
      }
    });

    return f;
  }

  public void init(HttpChunkRelayEventListener listener, MessageEvent e)
    throws Exception
  {
    state = State.init;

    inboundChannel.setReadable(false);

    ChannelFuture f = connect(msg);
    f.addListener(new ChannelFutureListener()
    {
      @Override
      public void operationComplete(ChannelFuture channelFuture)
        throws Exception
      {
        String objectName = "testobject";
        String uri = String.format("%s:%s/%s?uploads", remoteHost, remotePort, objectName);
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri);
        // set headers: http://docs.amazonwebservices.com/AmazonS3/latest/API/
        outboundChannel.write(request);
      }
    });
  }

  public void appendChunk(HttpChunk chunk)
  {

  }

  public void complete(HttpChunk chunk)
  {

  }

  public void abort()
  {

  }

  private class OutboundHandler extends SimpleChannelUpstreamHandler
  {

    private final Channel inboundChannel;

    OutboundHandler(Channel inboundChannel)
    {
      this.inboundChannel = inboundChannel;
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

      //System.out.println("<<< " + ChannelBuffers.hexDump(msg));
      synchronized (trafficLock)
      {
        // stages: init, update parts, complete
        if (state.equals(State.init))
        {
          if (m.getStatus() == HttpResponseStatus.OK)
          {
            inboundChannel.setReadable(true);
            state = State.appending;
          }
          else
          {
            state = State.closed;
            outboundChannel.close();
            // TODO: notify calling class
            return;
          }
        }
        else if (state.equals(State.appending))
        {
//        inboundChannel.write(msg);
          // If inboundChannel is saturated, do not read until notified in
          // HexDumpProxyInboundHandler.channelInterestChanged().
          if (!inboundChannel.isWritable())
          {
            e.getChannel().setReadable(false);
          }
        }
      }
    }

    @Override
    public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e)
      throws Exception
    {
      // If outboundChannel is not saturated anymore, continue accepting
      // the incoming traffic from the inboundChannel.
      synchronized (trafficLock)
      {
        if (e.getChannel().isWritable())
        {
          inboundChannel.setReadable(true);
        }
      }
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
      throws Exception
    {
      closeOnFlush(outboundChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
      throws Exception
    {
      e.getCause().printStackTrace();
      closeOnFlush(e.getChannel());
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

