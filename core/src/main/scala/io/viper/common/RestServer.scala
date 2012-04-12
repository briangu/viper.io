package io.viper.common


import org.jboss.netty.channel.{DefaultChannelPipeline, ChannelPipeline, ChannelPipelineFactory}
import collection.mutable.ArrayBuffer
import io.viper.core.server.router._


trait RestServer extends ChannelPipelineFactory
{
  val routes = new ArrayBuffer[Route]

  def getPipeline: ChannelPipeline =
  {
    import scala.collection.JavaConverters._
    val lhPipeline: ChannelPipeline = new DefaultChannelPipeline
    lhPipeline.addLast("uri-router", new RouterMatcherUpstreamHandler("uri-handlers", routes.toList.asJava))
    return lhPipeline
  }

  def addRoute(route: Route) = routes.append(route)

  def get(route: String, handler: RouteHandler)
  {
    addRoute(new GetRoute(route, handler))
  }

  def put(route: String, handler: RouteHandler)
  {
    addRoute(new PutRoute(route, handler))
  }

  def post(route: String, handler: RouteHandler)
  {
    addRoute(new PostRoute(route, handler))
  }

  def delete(route: String, handler: RouteHandler)
  {
    addRoute(new DeleteRoute(route, handler))
  }

  def addRoutes
}
