package io.viper.core.server.router;


import io.viper.core.server.StatusResponseHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;

import java.util.ArrayList;
import java.util.List;


public class RouterMatcherUpstreamHandler extends SimpleChannelUpstreamHandler
{
  private static final ChannelHandler HANDLER_404 = new StatusResponseHandler("Not found", 404);

  private final String _handlerName;

  List<Route> _routes = new ArrayList<Route>();

  public RouterMatcherUpstreamHandler(
    String handlerName,
    List<Route> routes)
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

    boolean matchFound = false;

    for (Route route : _routes)
    {
      if (!route.isMatch(request)) continue;
      setHandler(ctx.getPipeline(), route.getChannelHandler());
      matchFound = true;

      // rewrite the url to hide that the subsequent handlers are not at the root
      int routeLength = route.getRoute().length() - 1;
      if (routeLength > 0)
      {
        request.setUri(request.getUri().substring(routeLength));
      }

      break;
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
