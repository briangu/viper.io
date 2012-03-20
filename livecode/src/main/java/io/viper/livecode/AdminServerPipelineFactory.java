package io.viper.livecode;

import io.viper.core.server.file.FileContentInfoProvider;
import io.viper.core.server.file.StaticFileContentInfoProvider;
import io.viper.core.server.file.StaticFileServerHandler;
import io.viper.core.server.router.*;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.DefaultChannelPipeline;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdminServerPipelineFactory implements ChannelPipelineFactory
{
  final int _maxContentLength;
  final String _localhostName;
  final int _localhostPublicPort;
  final int _localhostAdminPort;
  final String _liveCodeRoot;
  final String _adminServerRoot;
  final FileContentInfoProvider _staticFileProvider;
  final String _adminHostname;
  final ViperHostRouterHandler _hostRouteHandler;

  public AdminServerPipelineFactory(
    int maxContentLength,
    String localhostName,
    int localhostPublicPort,
    int localhostAdminPort,
    String staticFileRoot,
    String uploadFileRoot,
    ViperHostRouterHandler hostRouterHandler)
      throws IOException, JSONException
  {
    _maxContentLength = maxContentLength;
    _localhostName = localhostName;
    _localhostPublicPort = localhostPublicPort;
    _localhostAdminPort = localhostAdminPort;
    _adminServerRoot = staticFileRoot;
    _liveCodeRoot = uploadFileRoot;
    _hostRouteHandler = hostRouterHandler;

    _adminHostname = String.format("%s:%d", localhostName, localhostAdminPort);

    _staticFileProvider = StaticFileContentInfoProvider.create(this.getClass(), _adminServerRoot);
  }

  @Override
  public ChannelPipeline getPipeline()
    throws Exception
  {
    List<Route> routes = new ArrayList<Route>();

    routes.add(new GetRoute("/code/$path", new RouteHandler()
    {
      @Override
      public RouteResponse exec(Map<String, String> args)
        throws Exception
      {
        return null;
      }
    }));

    routes.add(new PostRoute("/code/$path", new RouteHandler()
    {
      @Override
      public RouteResponse exec(Map<String, String> args)
        throws Exception
      {
        return null;
      }
    }));

    routes.add(new PostRoute("/reload", new RouteHandler()
    {
      @Override
      public RouteResponse exec(Map<String, String> args)
        throws Exception
      {
        _hostRouteHandler.reload();
        return null;
      }
    }));

    routes.add(new GetRoute("/$path", new StaticFileServerHandler(_staticFileProvider)));
    routes.add(new GetRoute("/", new StaticFileServerHandler(_staticFileProvider)));

    ChannelPipeline lhPipeline = new DefaultChannelPipeline();
    lhPipeline.addLast("decoder", new HttpRequestDecoder());
    lhPipeline.addLast("encoder", new HttpResponseEncoder());
    lhPipeline.addLast("router", new RouterMatcherUpstreamHandler("uri-handlers", routes));

    return lhPipeline;
  }
}

