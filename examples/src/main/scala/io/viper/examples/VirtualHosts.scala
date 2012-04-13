package io.viper.examples


import _root_.io.viper.core.server.router._
import io.viper.common.{NestServer, RestServer, ViperServer}
import java.util.Map
import org.json.JSONObject


object Main {
  def main(args: Array[String]) {
    val handler = new HostRouterHandler

    // Serve static.com from cached jar resources in the static.com directory
    handler.putRoute("static.com", new ViperServer("res:///static.com/"))

    // Serve REST handlers
    handler.putRoute("rest.com", new RestServer {
      def addRoutes {

        get("/hello", new RouteHandler {
          def exec(args: Map[String, String]): RouteResponse = new Utf8Response("world")
        })

        get("/echo/$something", new RouteHandler {
          def exec(args: Map[String, String]): RouteResponse = {
            val json = new JSONObject()
            json.put("response", args.get("$something"))
            new JsonResponse(json)
          }
        })
      }
    })

    NestServer.run(8080, handler)
  }
}
