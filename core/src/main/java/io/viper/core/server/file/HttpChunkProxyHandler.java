package io.viper.core.server.file;


import io.viper.core.server.Util;
import io.viper.core.server.router.Route;
import java.util.HashMap;
import java.util.Map;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;


public class HttpChunkProxyHandler extends Route
{
  private volatile HttpMessage _currentMessage;
  private volatile Channel _destChannel;
  private final int _maxContentLength;
  private int _currentByteCount;
  private final HttpChunkRelayProxy _chunkRelayProxy;
  private final HttpChunkRelayEventListener _relayListener;

  public HttpChunkProxyHandler(
    String route,
    HttpChunkRelayProxy chunkRelayProxy,
    HttpChunkRelayEventListener relayListener,
    int maxContentLength)
  {
    super(route);

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
      final HttpMessage m = (HttpMessage) msg;
      e.getChannel().setReadable(false);

      long contentLength =
        m.containsHeader("Content-Length")
          ? contentLength = Long.parseLong(m.getHeader("Content-Length"))
          : -1;

      String filename = m.containsHeader("X-File-Name") ? m.getHeader("X-File-Name") : null;
      String contentType = Util.getContentType(filename);

      Map<String, String> meta = new HashMap<String, String>();
      if (filename != null) meta.put("filename", filename);
      meta.put(HttpHeaders.Names.CONTENT_TYPE, contentType);
      String fileKey = _relayListener.onStart(meta);

      if (m.isChunked())
      {
        _currentMessage = m;
        _currentByteCount = 0;
        _destChannel = e.getChannel();

        _chunkRelayProxy.init(
          new HttpChunkProxyEventListener() {

            @Override
            public void onProxyConnected()
            {
            }

            @Override
            public void onProxyWriteReady()
            {
              _destChannel.setReadable(true);
            }

            @Override
            public void onProxyWritePaused()
            {
              _destChannel.setReadable(false);
            }

            @Override
            public void onProxyCompleted()
            {
              _relayListener.onCompleted(_destChannel);
            }

            @Override
            public void onProxyError()
            {
              if (_destChannel != null)
              {
                _destChannel.setReadable(false);
              }
              if (_currentMessage != null)
              {
                _currentMessage.setHeader(HttpHeaders.Names.WARNING, "failed to relay data");
              }
            }
          },
          fileKey,
          meta,
          contentLength);
      }
      else
      {
        final HttpChunk singleChunk = new LastHttpChunk(m.getContent());
        final Channel destChannel = e.getChannel();

        _chunkRelayProxy.init(
          new HttpChunkProxyEventListener() {

            @Override
            public void onProxyConnected()
            {
            }

            @Override
            public void onProxyWriteReady()
            {
              _chunkRelayProxy.writeChunk(singleChunk);
              destChannel.setReadable(true);
            }

            @Override
            public void onProxyWritePaused()
            {
            }

            @Override
            public void onProxyCompleted()
            {
              _relayListener.onCompleted(destChannel);
            }

            @Override
            public void onProxyError()
            {
              m.setHeader(HttpHeaders.Names.WARNING, "failed to relay data");
            }
          },
          fileKey,
          meta,
          contentLength);
      }
    }
    else
    {
      final HttpChunk chunk = (HttpChunk) msg;

      if (_maxContentLength != -1 && (_currentByteCount > (_maxContentLength - chunk.getContent().readableBytes())))
      {
        _currentMessage.setHeader(HttpHeaders.Names.WARNING, "maxContentLength exceeded");
        _chunkRelayProxy.abort();
        _destChannel.setReadable(false);
        _relayListener.onError(_destChannel);
      }
      else
      {
        _currentByteCount += chunk.getContent().readableBytes();

        _chunkRelayProxy.writeChunk(chunk);
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

    _relayListener.onError(_destChannel);

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

  private class LastHttpChunk implements HttpChunk
  {
    private ChannelBuffer content;

    public LastHttpChunk(ChannelBuffer content) {
        setContent(content);
    }

    @Override
    public ChannelBuffer getContent() {
        return content;
    }

    @Override
    public void setContent(ChannelBuffer content) {
        if (content == null) {
            throw new NullPointerException("content");
        }
        this.content = content;
    }

    @Override
    public boolean isLast() {
      return true;
    }
  }
}

