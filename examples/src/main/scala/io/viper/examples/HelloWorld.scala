package io.viper.examples


import _root_.io.viper.core.server.router._
import io.viper.common.{NestServer, RestServer}
import java.util.Map


object HelloWorld {
  def main(args: Array[String]) {
    NestServer.run(9080, new RestServer {
      def addRoutes {
        get("/hello", new RouteHandler {
          def exec(args: Map[String, String]): RouteResponse = new Utf8Response("world")
        })
      }
    })
  }
}
