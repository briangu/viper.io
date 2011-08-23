package viper.net.server.router;


import java.util.concurrent.ConcurrentHashMap;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;

import java.util.LinkedHashMap;
import java.util.Map;
import viper.net.server.StatusResponseHandler;


public class RouterHandler extends SimpleChannelUpstreamHandler
{
  private static final ChannelHandler HANDLER_404 = new StatusResponseHandler("Not found", 404);

  private final String _handlerName;

  ConcurrentHashMap<String, LinkedHashMap<Matcher, ChannelHandler>> _routes =
    new ConcurrentHashMap<String, LinkedHashMap<Matcher, ChannelHandler>>();

  public RouterHandler(
    String handlerName,
    ConcurrentHashMap<String, LinkedHashMap<Matcher, ChannelHandler>> routes)
  {
    _handlerName = handlerName;
    _routes = routes;
  }

  @Override
  public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
      throws Exception
  {
    if (!(e instanceof MessageEvent) || !(((MessageEvent) e).getMessage() instanceof HttpRequest))
    {
      super.handleUpstream(ctx, e);
      return;
    }

    HttpRequest request = (HttpRequest) ((MessageEvent) e).getMessage();

    if (!request.containsHeader(HttpHeaders.Names.HOST))
    {
      ctx.getPipeline().addLast(_handlerName, HANDLER_404);
      return;
    }

    String host = request.getHeader(HttpHeaders.Names.HOST);

    boolean matchFound = false;

    if (_routes.containsKey(host))
    {
      for (Map.Entry<Matcher, ChannelHandler> m : _routes.get(host).entrySet())
      {
        if (!m.getKey().match(request)) continue;
        setHandler(ctx.getPipeline(), m.getValue());
        matchFound = true;
        break;
      }
    }

    if (!matchFound)
    {
      setHandler(ctx.getPipeline(), HANDLER_404);
    }

    super.handleUpstream(ctx, e);
  }

  private void setHandler(ChannelPipeline p, ChannelHandler handler)
  {
    synchronized (p)
    {
      if (p.get(_handlerName) == null)
      {
        p.addLast(_handlerName, handler);
      }
      else
      {
        p.replace(_handlerName, _handlerName, handler);
      }
    }
  }
}