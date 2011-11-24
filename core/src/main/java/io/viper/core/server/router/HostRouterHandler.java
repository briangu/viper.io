package io.viper.core.server.router;


import io.viper.core.server.StatusResponseHandler;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;


public class HostRouterHandler extends SimpleChannelUpstreamHandler
{
  private static final ChannelHandler HANDLER_404 = new StatusResponseHandler("Not found", 404);

  ConcurrentHashMap<String, ChannelPipelineFactory> _routes = new ConcurrentHashMap<String, ChannelPipelineFactory>();

  private volatile ChannelPipeline _lastPipeline = null;

  public HostRouterHandler(ConcurrentHashMap<String, ChannelPipelineFactory> routes)
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
      hostPipeline = _routes.get(host).getPipeline();
    }
    else
    {
      setHandler(ctx.getPipeline(), "404-error", HANDLER_404);
      super.handleUpstream(ctx, e);
      return;
    }

    if (_lastPipeline != hostPipeline)
    {
      ChannelPipeline mainPipeline = ctx.getPipeline();

      while (mainPipeline.getLast() != this)
      {
        mainPipeline.removeLast();
      }

      for (String name : hostPipeline.getNames())
      {
        mainPipeline.addLast(name, hostPipeline.get(name));
      }

      _lastPipeline = hostPipeline;
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
