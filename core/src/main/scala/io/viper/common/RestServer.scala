package io.viper.common


import org.jboss.netty.channel.{DefaultChannelPipeline, ChannelPipeline, ChannelPipelineFactory}
import collection.mutable.ArrayBuffer
import io.viper.core.server.router._
import org.jboss.netty.handler.codec.http.{HttpResponseEncoder, HttpRequestDecoder}


trait RestServer extends ChannelPipelineFactory
{
  val routes: ArrayBuffer[Route] = new ArrayBuffer[Route]

  def getPipeline: ChannelPipeline = {
    routes.clear
    addRoutes
    buildPipeline
  }

  protected def buildPipeline: ChannelPipeline = {
    import scala.collection.JavaConverters._
    val lhPipeline = new DefaultChannelPipeline
    lhPipeline.addLast("rest-decoder", new HttpRequestDecoder)
    lhPipeline.addLast("rest-encoder", new HttpResponseEncoder)
    lhPipeline.addLast("rest-uri-router", new RouterMatcherUpstreamHandler("uri-handlers", routes.toList.asJava))
    lhPipeline
  }

  def addRoute(route: Route) = routes.append(route)

  def get(route: String, handler: RouteHandler) = addRoute(new GetRoute(route, handler))
  def put(route: String, handler: RouteHandler) = addRoute(new PutRoute(route, handler))
  def post(route: String, handler: RouteHandler) = addRoute(new PostRoute(route, handler))
  def delete(route: String, handler: RouteHandler) = addRoute(new DeleteRoute(route, handler))

  def addRoutes
}
