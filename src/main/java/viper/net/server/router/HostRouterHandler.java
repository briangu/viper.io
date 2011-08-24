package viper.net.server.router;


import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import viper.net.server.StatusResponseHandler;


public class HostRouterHandler extends SimpleChannelUpstreamHandler
{
  private static final ChannelHandler HANDLER_404 = new StatusResponseHandler("Not found", 404);

  ConcurrentHashMap<String, ChannelPipeline> _routes = new ConcurrentHashMap<String, ChannelPipeline>();

  public HostRouterHandler(ConcurrentHashMap<String, ChannelPipeline> routes)
  {
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
      setHandler(ctx.getPipeline(), "404-error", HANDLER_404);
      super.handleUpstream(ctx, e);
      return;
    }

    String host = request.getHeader(HttpHeaders.Names.HOST);

    ChannelPipeline hostPipeline = null;

    if (_routes.containsKey(host))
    {
      hostPipeline = _routes.get(host);
    }

    if (hostPipeline == null)
    {
      setHandler(ctx.getPipeline(), "404-error", HANDLER_404);
      super.handleUpstream(ctx, e);
      return;
    }

    ChannelPipeline mainPipeline = ctx.getPipeline();

    while (mainPipeline.getLast() != this)
    {
      mainPipeline.removeLast();
    }

    for (String name : hostPipeline.getNames())
    {
      mainPipeline.addLast(name, hostPipeline.get(name));
    }

    super.handleUpstream(ctx, e);
  }

  private void setHandler(ChannelPipeline p, String name, ChannelHandler handler)
  {
    synchronized (p)
    {
      if (p.get(name) == null)
      {
        p.addLast(name, handler);
      }
      else
      {
        p.replace(name, name, handler);
      }
    }
  }
}