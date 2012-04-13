package io.viper.examples


import _root_.io.viper.core.server.router._
import io.viper.common.{NestServer, RestServer}
import java.util.Map
import org.json.JSONObject


object Arguments {
  def main(args: Array[String]) {
    NestServer.run(8080, new RestServer {
      def addRoutes {
        get("/echo/$something", new RouteHandler {
          def exec(args: Map[String, String]): RouteResponse = {
            val json = new JSONObject()
            json.put("response", args.get("$something"))
            new JsonResponse(json)
          }
        })
        get("/do/$a/$b", new RouteHandler {
          def exec(args: Map[String, String]): RouteResponse = {
            val json = new JSONObject()
            json.put("a", args.get("$a"))
            json.put("b", args.get("$b"))
            new JsonResponse(json)
          }
        })
      }
    })
  }
}
