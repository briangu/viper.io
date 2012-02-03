package io.viper.livecode;


import io.viper.core.server.file.FileContentInfoProvider;
import io.viper.core.server.file.StaticFileContentInfoProvider;
import io.viper.core.server.file.StaticFileServerHandler;
import io.viper.core.server.router.GetRoute;
import io.viper.core.server.router.PostRoute;
import io.viper.core.server.router.Route;
import io.viper.core.server.router.RouteHandler;
import io.viper.core.server.router.RouteResponse;
import io.viper.core.server.router.RouterMatcherUpstreamHandler;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.DefaultChannelPipeline;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.json.JSONException;


public class LiveCodeServer
{
  private ServerBootstrap _bootstrap;

  static final ChannelGroup allChannels = new DefaultChannelGroup("server");

  public static LiveCodeServer create(
    int maxContentLength,
    String localhostName,
    int localhostPort,
    String staticFileRoot,
    String uploadFileRoot)
      throws Exception
  {
    LiveCodeServer liveCodeServer = new LiveCodeServer();

    liveCodeServer._bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));

    ChannelPipelineFactory pipelineFactory = new ServerPipelineFactory(maxContentLength, localhostName, localhostPort, staticFileRoot, uploadFileRoot);

    liveCodeServer._bootstrap.setOption("tcpNoDelay", true);
    liveCodeServer._bootstrap.setOption("keepAlive", true);

    liveCodeServer._bootstrap.setPipelineFactory(pipelineFactory);
    Channel channel = liveCodeServer._bootstrap.bind(new InetSocketAddress(localhostPort));

    allChannels.add(channel);

    return liveCodeServer;
  }

  public void shutdown()
  {
    ChannelGroupFuture future = allChannels.close();
    future.awaitUninterruptibly();
  }

  private static class ServerPipelineFactory implements ChannelPipelineFactory
  {
    final int _maxContentLength;
    final String _uploadFileRoot;
    final String _staticFileRoot;
    final FileContentInfoProvider _staticFileProvider;
    final FileContentInfoProvider _photoFileProvider;
    final String _localhostName;

    public ServerPipelineFactory(int maxContentLength,
                                 String localhostName,
                                 int localhostPort,
                                 String staticFileRoot,
                                 String uploadFileRoot)
      throws IOException, JSONException
    {
      _maxContentLength = maxContentLength;
      _uploadFileRoot = uploadFileRoot;
      _staticFileRoot = staticFileRoot;
      _localhostName = String.format("%s:%d", localhostName, localhostPort);

      _staticFileProvider = StaticFileContentInfoProvider.create(_staticFileRoot);
      _photoFileProvider = StaticFileContentInfoProvider.create(_uploadFileRoot);
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

      routes.add(new GetRoute("/d/$path", new StaticFileServerHandler(_photoFileProvider)));
      routes.add(new GetRoute("/$path", new StaticFileServerHandler(_staticFileProvider)));
      routes.add(new GetRoute("/", new StaticFileServerHandler(_staticFileProvider)));

      ChannelPipeline lhPipeline = new DefaultChannelPipeline();
      lhPipeline.addLast("decoder", new HttpRequestDecoder());
      lhPipeline.addLast("encoder", new HttpResponseEncoder());
      lhPipeline.addLast("router", new RouterMatcherUpstreamHandler("uri-handlers", routes));

      return lhPipeline;
    }
  }
}
