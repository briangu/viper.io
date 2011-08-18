package viper.net.server;

import java.util.UUID;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;

import java.util.List;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.json.JSONObject;


public class HttpChunkProxyHandler extends SimpleChannelUpstreamHandler implements HttpChunkProxyEventListener
{

  private volatile HttpMessage _currentMessage;
  private volatile Channel _inboundChannel;
  private final int _maxContentLength;
  private int _currentByteCount;
  private final HttpChunkRelayProxy _chunkRelayProxy;
  private final HttpChunkRelayEventListener _relayListener;

  public HttpChunkProxyHandler(
    HttpChunkRelayProxy chunkRelayProxy,
    HttpChunkRelayEventListener relayListener,
    int maxContentLength)
  {
    _chunkRelayProxy = chunkRelayProxy;
    _relayListener = relayListener;
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

        long contentLength =
          m.containsHeader("Content-Length")
            ? contentLength = Long.parseLong(m.getHeader("Content-Length"))
            : -1;

        String filename = m.getHeader("X-File-Name");

        _chunkRelayProxy.init(this, UUID.randomUUID().toString(), contentLength);
        _currentMessage = m;

        List<String> encodings = m.getHeaders(HttpHeaders.Names.TRANSFER_ENCODING);
        encodings.remove(HttpHeaders.Values.CHUNKED);
        if (encodings.isEmpty())
        {
          m.removeHeader(HttpHeaders.Names.TRANSFER_ENCODING);
        }
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

      if (_maxContentLength != -1 && (_currentByteCount > (_maxContentLength - chunk.getContent().readableBytes())))
      {
        _currentMessage.setHeader(HttpHeaders.Names.WARNING, "maxContentLength exceeded");
        _chunkRelayProxy.abort();
        _inboundChannel.setReadable(false);
        _relayListener.onError(_inboundChannel);
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
          _relayListener.onCompleted(_inboundChannel);
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

    _relayListener.onError(_inboundChannel);

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

  @Override
  public void onProxyCompleted()
  {

  }

  @Override
  public void onProxyError()
  {
    if (_inboundChannel != null)
    {
      _inboundChannel.setReadable(false);
    }
    if (_currentMessage != null)
    {
      _currentMessage.setHeader(HttpHeaders.Names.WARNING, "failed to relay data");
    }

//    Channels.fire?(ctx, _currentMessage, e.getRemoteAddress());
  }
}

