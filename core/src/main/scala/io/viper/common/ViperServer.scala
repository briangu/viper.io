package io.viper.common


import io.viper.core.server.file.StaticFileServerHandler
import org.jboss.netty.channel.{ChannelPipeline, ChannelPipelineFactory}
import java.util
import io.viper.core.server.router._
import collection.mutable.ListBuffer

class ViperServer(resourcePath: String) extends ChannelPipelineFactory with RestServer
{
  override def getPipeline: ChannelPipeline = {
    routes.clear()
    addRoutes
    addDefaultRoutes()
    buildPipeline
  }

  override def addRoutes {}

  private def addDefaultRoutes() {
    val provider = StaticFileContentInfoProviderFactory.create(this.getClass, resourcePath)
    val handler = new StaticFileServerHandler(provider)
    get("/$path", handler)
    get("/", handler)
  }
}

object VirtualServer {
  def apply(hostname: String): VirtualServer = new VirtualServer(hostname)
  def apply(hostname: String, resourcePath: String) = new VirtualServer(hostname, resourcePath)
}

class VirtualServer(val hostname: String, resourcePath: String) extends ViperServer(resourcePath) {
  def this(hostname: String) {
    this(hostname, "res:///%s".format(hostname))
  }

  private val initCode = new ListBuffer[() => Unit]

  override def addRoutes {
    for (proc <- initCode) proc()
  }

  def get(route: String, f:(util.Map[String, String]) => RouteResponse): VirtualServer = {
    initCode.append(() => { super.get(route)(f) })
    this
  }
  def put(route: String, f:(util.Map[String, String]) => RouteResponse): VirtualServer = {
    initCode.append(() => { super.put(route)(f) })
    this
  }
  def post(route: String, f:(util.Map[String, String]) => RouteResponse): VirtualServer = {
    initCode.append(() => { super.post(route)(f) })
    this
  }
  def delete(route: String, f:(util.Map[String, String]) => RouteResponse): VirtualServer = {
    initCode.append(() => { super.delete(route)(f) })
    this
  }
}