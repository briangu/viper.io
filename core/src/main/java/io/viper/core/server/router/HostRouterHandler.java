package io.viper.core.server.router;


import io.viper.core.server.StatusResponseHandler;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;

import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class HostRouterHandler extends SimpleChannelUpstreamHandler implements ChannelPipelineFactory
{
  private static final ChannelHandler HANDLER_404 = new StatusResponseHandler("Not found", 404);

  ConcurrentHashMap<String, ChannelPipelineFactory> _routeFactories = new ConcurrentHashMap<String, ChannelPipelineFactory>();

  public HostRouterHandler()
  {
  }

  public HostRouterHandler(ConcurrentHashMap<String, ChannelPipelineFactory> routes)
  {
    _routeFactories = routes;
  }

  public void putRoute(String host, ChannelPipelineFactory pipelineFactory) throws Exception
  {
    putRoute(host, 80, pipelineFactory);
  }

  public void putRoute(String host, int port, ChannelPipelineFactory pipelineFactory) throws Exception
  {
    if (host.equals("localhost"))
    {
      Set<String> aliases = getLocalHostAliases();
      for (String alias : aliases)
      {
        _routeFactories.put(String.format("%s:%d", alias, port), pipelineFactory);
      }
    }
    if (port == 80) _routeFactories.put(host, pipelineFactory);
    _routeFactories.put(String.format("%s:%d", host, port), pipelineFactory);
  }

  private Set<String> getLocalHostAliases()
  {
    Set<String> aliases = new HashSet<String>();

    try
    {
      Enumeration<NetworkInterface> netInterfaces = NetworkInterface.getNetworkInterfaces();

      while(netInterfaces.hasMoreElements()) {
        NetworkInterface ni = netInterfaces.nextElement();

        Enumeration<InetAddress> ipIter = ni.getInetAddresses();
        while(ipIter.hasMoreElements())
        {
          InetAddress ip = ipIter.nextElement();
          if (ip instanceof Inet4Address)
          {
            byte[] ipAddr = ip.getAddress();
            aliases.add(String.format("%d.%d.%d.%d",
              (short)(ipAddr[0] & 0xFF),
              (short)(ipAddr[1] & 0xFF),
              (short)(ipAddr[2] & 0xFF),
              (short)(ipAddr[3] & 0xFF)));

            String hostname = ip.getHostName();
            if (hostname != null)
            {
              aliases.add(hostname);
            }
          }
        }
      }
    }
    catch (SocketException e)
    {
    }

    return aliases;
  }

  public void removeRoute(String host)
  {
    _routeFactories.remove(host);
  }

  private class InternalHostRouterHandler extends SimpleChannelUpstreamHandler {

    private ConcurrentHashMap<String, ChannelPipeline> _routes = new ConcurrentHashMap<String, ChannelPipeline>();

    private volatile ChannelPipeline _lastPipeline = null;

    public InternalHostRouterHandler() throws Exception
    {
      updateRoutes();
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
      for (String route : _routeFactories.keySet())
      {
        _routes.put(route, _routeFactories.get(route).getPipeline());
      }
    }
  }

  @Override
  public ChannelPipeline getPipeline() throws Exception
  {
    ChannelPipeline pipeline = new DefaultChannelPipeline();
    pipeline.addLast("decoder", new HttpRequestDecoder());
    pipeline.addLast("encoder", new HttpResponseEncoder());
    pipeline.addLast("router", new InternalHostRouterHandler());
    return pipeline;
  }
}
