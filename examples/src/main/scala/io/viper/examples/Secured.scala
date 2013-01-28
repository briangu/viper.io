package io.viper.examples

import _root_.io.viper.core.server.router._
import io.viper.common.{NestServer, RestServer}
import java.util.Map
import io.viper.core.server.security.{HttpBasicAuthHandler, AuthHandler}
import org.jboss.netty.handler.codec.http.HttpRequest


object Secured {
  def main(args: Array[String]) {
    NestServer.run(9080, new RestServer {
      def addRoutes {
        get("/secured", new RouteHandler {
          def exec(args: Map[String, String]): RouteResponse = new Utf8Response("world")
        }, new HttpBasicAuthHandler {
          def isAuthorized(request: HttpRequest, username: String, password: String): Boolean = (username.equals("admin") && password.equals("admin"))
        })
      }
    })
  }
}
