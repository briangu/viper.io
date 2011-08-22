package viper.net.server;


import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.util.CharsetUtil;

import java.util.concurrent.ConcurrentHashMap;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;


public class CacheHandler extends SimpleChannelHandler
{
  ConcurrentHashMap<String, ConcurrentHashMap<String, CacheEntry>> _cache =
    new ConcurrentHashMap<String, ConcurrentHashMap<String, CacheEntry>>();

  @Override
  public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
    throws Exception
  {
    if ((e instanceof MessageEvent) && (((MessageEvent) e).getMessage() instanceof HttpRequest))
    {
      HttpRequest request = (HttpRequest) ((MessageEvent) e).getMessage();

      if (request.containsHeader("Host") && _cache.contains(request.getHeader("Host")))
      {
        ConcurrentHashMap<String, CacheEntry> hostCache = _cache.get(request.getUri());

        CacheEntry ce = hostCache.get(request.getUri());
        if (ce != null)
        {
          if (ce.expires > System.currentTimeMillis())
          {
            ChannelFuture f = e.getChannel().write(ce.content);
            if (!HttpHeaders.isKeepAlive(request))
            {
              f.addListener(ChannelFutureListener.CLOSE);
            }
            return;
          }
          else
          {
            if (ce.content instanceof CachableHttpResponse)
            {
              CachableHttpResponse r = (CachableHttpResponse) ce.content;
              r.dispose();
            }
            _cache.remove(ce);
          }
        }
      }
    }

    super.handleUpstream(ctx, e);
  }

  @Override
  public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e)
    throws Exception
  {
    if ((e instanceof MessageEvent) && ((MessageEvent) e).getMessage() instanceof CachableHttpResponse)
    {
      CachableHttpResponse r = (CachableHttpResponse) ((MessageEvent) e).getMessage();

      if (r.getCacheMaxAge() > 0)
      {
        String host = r.getHost();

        if (!_cache.contains(host))
        {
          _cache.put(host, new ConcurrentHashMap<String, CacheEntry>());
        }

        ConcurrentHashMap<String, CacheEntry> hostCache = _cache.get(host);

        hostCache.putIfAbsent(
          r.getRequestUri(),
          new CacheEntry(r, System.currentTimeMillis() + r.getCacheMaxAge() * 1000));
      }
    }

    super.handleDownstream(ctx, e);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    throws Exception
  {
    Channel ch = e.getChannel();
    Throwable cause = e.getCause();

    cause.printStackTrace();
    if (ch.isConnected())
    {
      sendError(ctx, INTERNAL_SERVER_ERROR);
    }
  }

  private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status)
  {
    HttpResponse response = new DefaultHttpResponse(HTTP_1_1, status);
    response.setHeader(CONTENT_TYPE, "text/plain; charset=UTF-8");
    response.setContent(ChannelBuffers.copiedBuffer("Failure: " + status.toString() + "\r\n", CharsetUtil.UTF_8));

    // Close the connection as soon as the error message is sent.
    ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
  }

  private static class CacheEntry
  {
    public HttpMessage content;
    public long expires;

    private CacheEntry(HttpMessage content, long expires)
    {
      this.content = content;
      this.expires = expires;
    }
  }
}
