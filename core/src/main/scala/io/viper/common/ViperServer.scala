package io.viper.common


import io.viper.core.server.file.StaticFileServerHandler
import org.jboss.netty.channel.{ChannelPipeline, ChannelPipelineFactory}


class ViperServer(resourcePath: String) extends ChannelPipelineFactory with RestServer
{
  override def getPipeline: ChannelPipeline = {
    addDefaultRoutes
    super.getPipeline
  }

  override def addRoutes = {}

  private def addDefaultRoutes {
    val provider = StaticFileContentInfoProviderFactory.create(this.getClass, resourcePath)
    get("/$path", new StaticFileServerHandler(provider))
    get("/", new StaticFileServerHandler(provider))
  }
}
