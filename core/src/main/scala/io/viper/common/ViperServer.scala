package io.viper.common


import io.viper.core.server.file.StaticFileServerHandler
import org.jboss.netty.channel.{DefaultChannelPipeline, ChannelPipeline, ChannelPipelineFactory}
import org.jboss.netty.handler.codec.http.{HttpResponseEncoder, HttpRequestDecoder}
import io.viper.core.server.router.RouterMatcherUpstreamHandler


class ViperServer(resourcePath: String) extends ChannelPipelineFactory with RestServer
{
  override def getPipeline: ChannelPipeline = {
    routes.clear
    addRoutes
    addDefaultRoutes
    buildPipeline
  }

  override def addRoutes = {}

  private def addDefaultRoutes {
    val provider = StaticFileContentInfoProviderFactory.create(this.getClass, resourcePath)
    val handler = new StaticFileServerHandler(provider)
    get("/$path", handler)
    get("/", handler)
  }
}
