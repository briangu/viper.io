package io.viper.examples


import _root_.io.viper.core.server.router._
import io.viper.common.{NestServer, RestServer}
import java.util.Map
import org.json.JSONObject
import collection.mutable.HashMap
import org.jboss.netty.handler.codec.http.{HttpResponseStatus, HttpVersion, DefaultHttpResponse, HttpResponse}

/**
 * Demonstrate basic REST resource CRUD operations using arguments
 *
 * TODO: Would be cooler if the CRUD set was auto-generated
 *       argument extraction could be cleaner and with stronger typing
 *
 */
object Arguments {

  // NOT thread-safe, just for demo purposes
  val mem = new HashMap[String, String]

  def main(args: Array[String]) {
    NestServer.run(8080, new RestServer {
      def addRoutes {

        get("/mem/$key", new RouteHandler {
          def exec(args: Map[String, String]): RouteResponse = {
            val key = args.get("key")
            mem.get(key) match {
              case Some(value) => keyResponse(key, value)
              case None => new StatusResponse(HttpResponseStatus.NOT_FOUND)
            }
          }
        })

        delete("/mem/$key", new RouteHandler {
          def exec(args: Map[String, String]): RouteResponse = {
            val key = args.get("key")
            mem.remove(key) match {
              case Some(value) => keyResponse(key, value)
              case None => new StatusResponse(HttpResponseStatus.NO_CONTENT)
            }
          }
        })

        put("/mem", new RouteHandler {
          def exec(args: Map[String, String]): RouteResponse = {
            val (key, value) = getParams(args)
            if (mem.contains(key)) {
              mem.put(key, value)
              keyResponse(key, value)
            } else {
              new StatusResponse(HttpResponseStatus.NOT_FOUND)
            }
          }
        })

        post("/mem", new RouteHandler {
          def exec(args: Map[String, String]): RouteResponse = {
            val (key, value) = getParams(args)
            mem.put(key, value)
            keyResponse(key, value)
          }
        })
      }
    })
  }

  // TODO: we should fail with a 400 if we don't have the args we need
  //       the platform should take care of validating this
  def getParams(args: Map[String, String]) : (String, String) = {
    if (!args.containsKey("key")) throw new RuntimeException("missing param: key")
    if (!args.containsKey("value")) throw new RuntimeException("missing param: value")

    (args.get("key"), args.get("value"))
  }

  def keyResponse(key: String, value: String): JsonResponse = {
    val json = new JSONObject()
    json.put(key, value)
    new JsonResponse(json)
  }
}
