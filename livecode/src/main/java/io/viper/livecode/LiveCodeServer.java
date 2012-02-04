package io.viper.livecode;


import io.viper.core.server.router.*;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;


public class LiveCodeServer
{
  private ServerBootstrap _adminServer;
  private ServerBootstrap _publicServers;

  static final ChannelGroup _allChannels = new DefaultChannelGroup("server");

  public static LiveCodeServer create(
    int maxContentLength,
    String localhostName,
    int localhostPublicPort,
    int localhostAdminPort,
    String adminServerRoot,
    String liveCodeRoot)
      throws Exception
  {
    LiveCodeServer liveCodeServer = new LiveCodeServer();

    HostRouterHandler hostRouterHandler = new HostRouterHandler();

    liveCodeServer._publicServers = createHostedServers(hostRouterHandler);

    liveCodeServer._adminServer = createAdminServer(
      maxContentLength,
      localhostName,
      localhostPublicPort,
      localhostAdminPort,
      adminServerRoot,
      liveCodeRoot,
      hostRouterHandler);

    _allChannels.add(liveCodeServer._publicServers.bind(new InetSocketAddress(localhostPublicPort)));
    _allChannels.add(liveCodeServer._adminServer.bind(new InetSocketAddress(localhostAdminPort)));

    return liveCodeServer;
  }

  private static ServerBootstrap createAdminServer(
    int maxContentLength,
    String localhostName,
    int localhostPublicPort,
    int localhostAdminPort,
    String adminServerRoot,
    String liveCodeRoot,
    HostRouterHandler hostRouterHandler)
      throws Exception
  {
    ServerBootstrap server = new ServerBootstrap(new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));

    ChannelPipelineFactory adminPipeLineFactory =
      new AdminServerPipelineFactory(
        maxContentLength,
        localhostName,
        localhostPublicPort,
        localhostAdminPort,
        adminServerRoot,
        liveCodeRoot,
        hostRouterHandler);

    server.setOption("tcpNoDelay", true);
    server.setOption("keepAlive", true);

    server.setPipelineFactory(adminPipeLineFactory);

    return server;
  }

  private static ServerBootstrap createHostedServers(HostRouterHandler hostRouterHandler)
  {
    ServerBootstrap server = new ServerBootstrap(new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));

    server.setOption("tcpNoDelay", true);
    server.setOption("keepAlive", true);

    server.setPipelineFactory(hostRouterHandler);

    return server;
  }

  public void shutdown()
  {
    ChannelGroupFuture future = _allChannels.close();
    future.awaitUninterruptibly();
  }
}
