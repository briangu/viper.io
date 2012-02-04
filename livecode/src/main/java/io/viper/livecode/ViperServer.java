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

public class ViperServer implements ChannelPipelineFactory
{
  final int _maxContentLength;
  final String _localhostName;
  final int _localhostPort;
  final String _staticFileRoot;
  final List<Route> _routes;

  final String _hostname;

  public ViperServer(
    int maxContentLength,
    String localhostName,
    int localhostPort,
    String staticFileRoot,
    List<Route> routes
  )
  {
    _maxContentLength = maxContentLength;
    _localhostName = localhostName;
    _localhostPort = localhostPort;
    _staticFileRoot = staticFileRoot;
    _routes = routes;

    _hostname = String.format("%s:%d", localhostName, localhostPort);
  }

  @Override
  public ChannelPipeline getPipeline()
    throws Exception
  {
    List<Route> routes = new ArrayList<Route>(_routes);

    if (_staticFileRoot != null && _staticFileRoot.length() > 0)
    {
      StaticFileContentInfoProvider staticFileProvider = StaticFileContentInfoProvider.create(_staticFileRoot);
      routes.add(new GetRoute("/$path", new StaticFileServerHandler(staticFileProvider)));
      routes.add(new GetRoute("/", new StaticFileServerHandler(staticFileProvider)));
    }

    ChannelPipeline lhPipeline = new DefaultChannelPipeline();
    lhPipeline.addLast("decoder", new HttpRequestDecoder());
    lhPipeline.addLast("encoder", new HttpResponseEncoder());
    lhPipeline.addLast("router", new RouterMatcherUpstreamHandler("uri-handlers", routes));

    return lhPipeline;
  }
}

