package io.viper.core.server.router;


import io.viper.core.server.StatusResponseHandler;

import java.util.concurrent.ConcurrentHashMap;

import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;


public class HostRouterHandler extends SimpleChannelUpstreamHandler implements ChannelPipelineFactory
{
  private static final ChannelHandler HANDLER_404 = new StatusResponseHandler("Not found", 404);

  ConcurrentHashMap<String, ChannelPipelineFactory> _routeFactories = new ConcurrentHashMap<String, ChannelPipelineFactory>();

  private ConcurrentHashMap<String, ChannelPipeline> _routes = new ConcurrentHashMap<String, ChannelPipeline>();
  
  private volatile ChannelPipeline _lastPipeline = null;

  public HostRouterHandler()
  {
  }

  public HostRouterHandler(ConcurrentHashMap<String, ChannelPipelineFactory> routes)
  {
    _routeFactories = routes;
  }

  public void putRoute(String host, ChannelPipelineFactory pipelineFactory) throws Exception
  {
    _routeFactories.put(host, pipelineFactory);
    _routes.put(host, pipelineFactory.getPipeline());
  }

  public void removeRoute(String host)
  {
    _routeFactories.remove(host);
    _routes.remove(host);
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

    ChannelPipeline hostPipeline;

    if (_routes.containsKey(host))
    {
      hostPipeline = _routes.get(host);
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

  private void updateRoutes() throws Exception
  {
    _routes.clear();

    for (String route : _routeFactories.keySet())
    {
      _routes.put(route, _routeFactories.get(route).getPipeline());
    }
  }

  @Override
  public ChannelPipeline getPipeline() throws Exception
  {
    updateRoutes();

    ChannelPipeline pipeline = new DefaultChannelPipeline();
    pipeline.addLast("decoder", new HttpRequestDecoder());
    pipeline.addLast("encoder", new HttpResponseEncoder());
    pipeline.addLast("router", this);
    return pipeline;
  }
}
