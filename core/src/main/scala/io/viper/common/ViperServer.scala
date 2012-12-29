package io.viper.common


import io.viper.core.server.file.StaticFileServerHandler
import org.jboss.netty.channel.{ChannelPipeline, ChannelPipelineFactory}


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

class VirtualServer(val hostname: String, resourcePath: String = "") extends ViperServer(resourcePath) {}