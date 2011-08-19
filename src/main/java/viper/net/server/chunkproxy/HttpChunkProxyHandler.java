package viper.net.server.chunkproxy;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import viper.net.server.Util;


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
        _currentMessage = m;
        _currentByteCount = 0;
        _inboundChannel = e.getChannel();

        long contentLength =
          m.containsHeader("Content-Length")
            ? contentLength = Long.parseLong(m.getHeader("Content-Length"))
            : -1;

        String filename = m.containsHeader("X-File-Name") ? m.getHeader("X-File-Name") : null;
        String contentType = Util.getContentType(filename);

        _chunkRelayProxy.init(
          this,
          filename,
          contentLength,
          contentType);

/*
        List<String> encodings = m.getHeaders(HttpHeaders.Names.TRANSFER_ENCODING);
        encodings.remove(HttpHeaders.Values.CHUNKED);
        if (encodings.isEmpty())
        {
          m.removeHeader(HttpHeaders.Names.TRANSFER_ENCODING);
        }
*/

        Map<String, String> props = new HashMap<String, String>();
        if (filename != null)
        {
          props.put("X-File-Name", filename);
        }
        props.put(HttpHeaders.Names.CONTENT_LENGTH, Long.toString(contentLength));
        props.put(HttpHeaders.Names.CONTENT_TYPE, contentType);

        _relayListener.onStart(props);
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
    if (_chunkRelayProxy.isRelaying())
    {
      _chunkRelayProxy.abort();
    }

    closeOnFlush(e.getChannel());
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    throws Exception
  {
    e.getCause().printStackTrace();

    _relayListener.onError(_inboundChannel);

    if (_chunkRelayProxy.isRelaying())
    {
      _chunkRelayProxy.abort();
    }

    closeOnFlush(e.getChannel());
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
  }
}

