package io.viper.common


import java.net.InetSocketAddress
import java.util.concurrent.Executors
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.group.{DefaultChannelGroup, ChannelGroup}
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.channel.ChannelPipelineFactory


object NestServer
{
  val MAX_CONTENT_LENGTH = 1024 * 1024 * 1024;

  val _allChannels: ChannelGroup = new DefaultChannelGroup("server")
  var _virtualServers: ServerBootstrap = null

  def create(localhostPort: Int, handler: ChannelPipelineFactory) {
    create(MAX_CONTENT_LENGTH, localhostPort, handler)
  }

  def create(maxContentLength: Int, localhostPort: Int, handler: ChannelPipelineFactory) {
    _virtualServers = createServer(maxContentLength, localhostPort, handler)
    _allChannels.add(_virtualServers.bind(new InetSocketAddress(localhostPort)))
  }

  private def createServer(maxContentLength: Int, port: Int, handler: ChannelPipelineFactory): ServerBootstrap = {
    val server = new ServerBootstrap(new NioServerSocketChannelFactory(Executors.newCachedThreadPool,
                                                                       Executors.newCachedThreadPool))
    server.setOption("tcpNoDelay", true)
    server.setOption("keepAlive", true)
    server.setPipelineFactory(handler)
    server
  }

  def run(handler: ChannelPipelineFactory) {
    run(80, handler)
  }

  def run(localhostPort: Int, handler: ChannelPipelineFactory) {
    create(MAX_CONTENT_LENGTH, localhostPort, handler)
    Thread.currentThread.join
  }

  def run(localhostPort: Int)(f:(RestServer) => Unit) {
    val handler = new RestServer {
      def addRoutes {
        f(this)
      }
    }
    create(MAX_CONTENT_LENGTH, localhostPort, handler)
    Thread.currentThread.join
  }

  def shutdown {
    _allChannels.close.awaitUninterruptibly()
  }
}


