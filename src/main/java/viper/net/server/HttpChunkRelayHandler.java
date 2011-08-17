package viper.net.server;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;

import java.util.List;


public class HttpChunkRelayHandler extends SimpleChannelUpstreamHandler implements HttpChunkRelayEventListener
{

  private volatile HttpMessage _currentMessage;
  private volatile Channel _inboundChannel;
  private final int _maxContentLength;
  private int _currentByteCount;
  private final HttpChunkRelayProxy _chunkRelayProxy;

  public HttpChunkRelayHandler(
      HttpChunkRelayProxy chunkRelayProxy,
      int maxContentLength)
  {
    _chunkRelayProxy = chunkRelayProxy;
    _maxContentLength = maxContentLength;
  }

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
    throws Exception
  {
    Object msg = e.getMessage();
    if (!(msg instanceof HttpMessage) && !(msg instanceof HttpChunk))
    {
      ctx.sendUpstream(e);
      return;
    }

    if (_currentMessage == null)
    {
      HttpMessage m = (HttpMessage) msg;
      if (m.isChunked())
      {
        _currentByteCount = 0;
        _inboundChannel = e.getChannel();

        _chunkRelayProxy.init(this, e);

        List<String> encodings = m.getHeaders(HttpHeaders.Names.TRANSFER_ENCODING);
        encodings.remove(HttpHeaders.Values.CHUNKED);
        if (encodings.isEmpty())
        {
          m.removeHeader(HttpHeaders.Names.TRANSFER_ENCODING);
        }

        this._currentMessage = m;
      }
      else
      {
        // Not a chunked message - pass through.
        ctx.sendUpstream(e);
      }
    }
    else
    {
      final HttpChunk chunk = (HttpChunk) msg;

      if (_maxContentLength != -1 && (_currentByteCount > (_maxContentLength - chunk.getContent().readableBytes()))) {
        _currentMessage.setHeader(HttpHeaders.Names.WARNING, "maxContentLength exceeded");
        _chunkRelayProxy.abort();
        _inboundChannel.setReadable(false);
      }
      else
      {
        _currentByteCount += chunk.getContent().readableBytes();

        if (!chunk.isLast())
        {
          _chunkRelayProxy.appendChunk(chunk);
        }
        else
        {
          _chunkRelayProxy.complete(chunk);
          Channels.fireMessageReceived(ctx, _currentMessage, e.getRemoteAddress());
        }
      }
    }
  }

  @Override
  public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
    throws Exception
  {
    closeOnFlush(e.getChannel());
    if (_chunkRelayProxy.isRelaying())
    {
      _chunkRelayProxy.abort();
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    throws Exception
  {
    e.getCause().printStackTrace();
    closeOnFlush(e.getChannel());
    if (_chunkRelayProxy.isRelaying())
    {
      _chunkRelayProxy.abort();
    }
  }

  static void closeOnFlush(Channel ch)
  {
    if (ch.isConnected())
    {
      ch.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }
  }

  @Override
  public void onProxyReady()
  {
    _inboundChannel.setReadable(true);
  }

  @Override
  public void onProxyPaused()
  {
    _inboundChannel.setReadable(false);
  }
}

