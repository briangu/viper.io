package io.viper.common


import java.net.InetSocketAddress
import java.util.concurrent.Executors
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.group.{DefaultChannelGroup, ChannelGroup}
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import io.viper.core.server.router._


object NestServer
{

  val MAX_CONTENT_LENGTH = 1024 * 1024 * 1024;

  val _allChannels: ChannelGroup = new DefaultChannelGroup("server")
  var _virtualServers: ServerBootstrap = null

  def create(localhostPort: Int, hostRouterHandler: HostRouterHandler)
  {
    create(MAX_CONTENT_LENGTH, localhostPort, hostRouterHandler)
  }

  def create(maxContentLength: Int, localhostPort: Int, hostRouterHandler: HostRouterHandler)
  {
    _virtualServers = createServer(maxContentLength, localhostPort, hostRouterHandler)
    _allChannels.add(_virtualServers.bind(new InetSocketAddress(localhostPort)))
  }

  private def createServer(maxContentLength: Int,
                           localhostPort: Int,
                           hostRouterHandler: HostRouterHandler): ServerBootstrap =
  {
    val server = new ServerBootstrap(new NioServerSocketChannelFactory(Executors.newCachedThreadPool,
                                                                       Executors.newCachedThreadPool))
    server.setOption("tcpNoDelay", true)
    server.setOption("keepAlive", true)
    server.setPipelineFactory(hostRouterHandler)
    return server
  }

  def run(hostRouterHandler: HostRouterHandler) {
    run(80, hostRouterHandler)
  }

  def run(localhostPort: Int, hostRouterHandler: HostRouterHandler) {
    create(MAX_CONTENT_LENGTH, localhostPort, hostRouterHandler)
    Thread.currentThread.join
  }

  def shutdown
  {
    _allChannels.close.awaitUninterruptibly()
  }
}


