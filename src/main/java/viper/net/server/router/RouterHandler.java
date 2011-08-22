package viper.net.server.router;


import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;

import java.util.LinkedHashMap;
import java.util.Map;
import viper.net.server.StatusResponseHandler;


public class RouterHandler extends SimpleChannelUpstreamHandler
{
  private static final ChannelHandler HANDLER_404 = new StatusResponseHandler("Not found", 404);

  private final String _handlerName;
  private final LinkedHashMap<Matcher, ChannelHandler> _routes;

  public RouterHandler(String handlerName, LinkedHashMap<Matcher, ChannelHandler> routes)
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

    for (Map.Entry<Matcher, ChannelHandler> m : _routes.entrySet())
    {
      if (!m.getKey().match(request)) continue;

      ChannelPipeline p = ctx.getPipeline();
      synchronized (p)
      {
        if (p.get(_handlerName) == null)
        {
          p.addLast(_handlerName, m.getValue());
        }
        else
        {
          p.replace(_handlerName, _handlerName, m.getValue());
        }
      }

      matchFound = true;
      break;
    }

    if (!matchFound)
    {
      ctx.getPipeline().addLast(_handlerName, HANDLER_404);
    }

    super.handleUpstream(ctx, e);
  }
}