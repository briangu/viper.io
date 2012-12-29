package io.viper.common


import java.net.InetSocketAddress
import java.util.concurrent.Executors
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.group.{DefaultChannelGroup, ChannelGroup}
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.channel.ChannelPipelineFactory
import io.viper.core.server.router._
import java.util
import collection.immutable
import collection.mutable.ListBuffer


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

class NestServer(val port: Int = 80) extends DelayedInit {
  import NestServer._

  protected def args: Array[String] = _args
  protected def server: RestServer = _server

  private var _server: RestServer = _
  private var _args: Array[String] = _

  override def delayedInit(body: => Unit) {
    //initCode += (() => body)
    _server = new RestServer {
      def addRoutes {
        body
      }
    }
  }

  def get(route: String)(f:(util.Map[String, String]) => RouteResponse) {
    val handler = new RouteHandler {
      def exec(args: util.Map[String, String]) = f(args)
    }
    immutable.List() map {idx => idx}
    server.addRoute(new GetRoute(route, handler))
  }
  def put(route: String)(f:(util.Map[String, String]) => RouteResponse) {
    val handler = new RouteHandler {
      def exec(args: util.Map[String, String]) = f(args)
    }
    server.addRoute(new PutRoute(route, handler))
  }
  def post(route: String)(f:(util.Map[String, String]) => RouteResponse) {
    val handler = new RouteHandler {
      def exec(args: util.Map[String, String]) = f(args)
    }
    server.addRoute(new PostRoute(route, handler))
  }
  def delete(route: String)(f:(util.Map[String, String]) => RouteResponse) {
    val handler = new RouteHandler {
      def exec(args: util.Map[String, String]) = f(args)
    }
    server.addRoute(new DeleteRoute(route, handler))
  }


  /** The main method.
    *  This stores all argument so that they can be retrieved with `args`
    *  and the executes all initialization code segments in the order they were
    *  passed to `delayedInit`
    *  @param args the arguments passed to the main method
    */
  def main(args: Array[String]) {
    this._args = args

    create(MAX_CONTENT_LENGTH, port, server)
    Thread.currentThread.join()
  }
}

class StaticServer(resourcePath: String, port: Int = 80) extends App {
  import NestServer._
  create(MAX_CONTENT_LENGTH, port, new ViperServer(resourcePath))
  Thread.currentThread.join()
}

class MultiHostServer(port: Int = 80) extends DelayedInit {
  import NestServer._

  protected def args: Array[String] = _args
  protected def server: HostRouterHandler = _handler

  private val _handler = new HostRouterHandler

  private var _args: Array[String] = _
  private val initCode = new ListBuffer[() => Unit]

  override def delayedInit(body: => Unit) {
    initCode += (() => body)
  }

  def route(hostname: String, resourcePath: String) {
    _handler.putRoute(hostname, new ViperServer(resourcePath))
  }

  def route(hostname: String, server: ViperServer) {
    _handler.putRoute(hostname, server)
  }

  def route(server: VirtualServer) {
    _handler.putRoute(server.hostname, server)
  }

  def route(hostname: String) (f:(RestServer) => Unit) {
    _handler.putRoute(hostname, new RestServer {
      def addRoutes {
        f(this)
      }
    })
  }

  /** The main method.
    *  This stores all argument so that they can be retrieved with `args`
    *  and the executes all initialization code segments in the order they were
    *  passed to `delayedInit`
    *  @param args the arguments passed to the main method
    */
  def main(args: Array[String]) {
    this._args = args
    for (proc <- initCode) proc()
    create(MAX_CONTENT_LENGTH, port, server)
    Thread.currentThread.join()
  }
}