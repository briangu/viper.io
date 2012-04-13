package io.viper.common


import io.viper.core.server.file.StaticFileServerHandler
import org.jboss.netty.channel.{DefaultChannelPipeline, ChannelPipeline, ChannelPipelineFactory}
import collection.mutable.ArrayBuffer
import io.viper.core.server.router._
import java.util.Map


class ViperServer(resourcePath: String) extends ChannelPipelineFactory with RestServer
{
  override def getPipeline: ChannelPipeline =
  {
    import scala.collection.JavaConverters._
    val routes = addDefaultRoutes
    val lhPipeline: ChannelPipeline = new DefaultChannelPipeline
    lhPipeline.addLast("uri-router", new RouterMatcherUpstreamHandler("uri-handlers", routes.toList.asJava))
    return lhPipeline
  }

  override def addRoutes = {}

  private def addDefaultRoutes: ArrayBuffer[Route] =
  {
    val routes = ArrayBuffer[Route]()
    routes.append(new GetRoute("/hello", new RouteHandler
    {
      def exec(args: Map[String, String]): RouteResponse = new Utf8Response("world")
    }))
    val provider = StaticFileContentInfoProviderFactory.create(this.getClass, resourcePath)
    routes.append(new GetRoute("/$path", new StaticFileServerHandler(provider)))
    routes.append(new GetRoute("/", new StaticFileServerHandler(provider)))
    routes
  }
}
