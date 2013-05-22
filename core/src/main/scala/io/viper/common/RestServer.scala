package io.viper.common


import org.jboss.netty.channel.{DefaultChannelPipeline, ChannelPipeline, ChannelPipelineFactory}
import collection.mutable.ArrayBuffer
import io.viper.core.server.router._
import org.jboss.netty.handler.codec.http.{HttpChunkAggregator, HttpResponseEncoder, HttpRequestDecoder}
import java.util
import collection.immutable
import io.viper.core.server.security.AuthHandler

trait RestServer extends ChannelPipelineFactory
{
  val routes: ArrayBuffer[Route] = new ArrayBuffer[Route]

  val MAX_CONTENT_LENGTH = 1024*1024*1024

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
    lhPipeline.addLast("rest-chunker", new HttpChunkAggregator(MAX_CONTENT_LENGTH))
    lhPipeline.addLast("rest-uri-router", new RouterMatcherUpstreamHandler("uri-handlers", routes.toList.asJava))
    lhPipeline
  }

  def addRoute(route: Route) = {
    routes.append(route)
  }

  def get(route: String, handler: RouteHandler, authHandler: AuthHandler = null) = addRoute(new GetRoute(route, handler, authHandler))
  def put(route: String, handler: RouteHandler, authHandler: AuthHandler = null) = addRoute(new PutRoute(route, handler, authHandler))
  def post(route: String, handler: RouteHandler, authHandler: AuthHandler = null) = addRoute(new PostRoute(route, handler, authHandler))
  def delete(route: String, handler: RouteHandler, authHandler: AuthHandler = null) = addRoute(new DeleteRoute(route, handler, authHandler))

  def get(route: String)(f:(util.Map[String, String]) => RouteResponse) {
    val handler = new RouteHandler {
      def exec(args: util.Map[String, String]) = f(args)
    }
    immutable.List() map {idx => idx}
    addRoute(new GetRoute(route, handler))
  }
  def put(route: String)(f:(util.Map[String, String]) => RouteResponse) {
    val handler = new RouteHandler {
      def exec(args: util.Map[String, String]) = f(args)
    }
    addRoute(new PutRoute(route, handler))
  }
  def post(route: String)(f:(util.Map[String, String]) => RouteResponse) {
    val handler = new RouteHandler {
      def exec(args: util.Map[String, String]) = f(args)
    }
    addRoute(new PostRoute(route, handler))
  }
  def delete(route: String)(f:(util.Map[String, String]) => RouteResponse) {
    val handler = new RouteHandler {
      def exec(args: util.Map[String, String]) = f(args)
    }
    addRoute(new DeleteRoute(route, handler))
  }

  def addRoutes
}