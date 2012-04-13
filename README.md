viper.io
========

Viper.io is an http powertool written on top of Netty.  It's designed to simplify:

* virtual hosting (host multiple domains from the same process)
* read-only websites (splash or product pages)
* REST application development (e.g., client-side templates that consume JSON)
* Uploading files directly to S3 using Netty
* Dynamic thumbnail generation from images

Example
-------

Hello World:

    package io.viper.examples


    import _root_.io.viper.core.server.router._
    import io.viper.common.{NestServer, RestServer}
    import java.util.Map


    object Main {
      def main(args: Array[String]) {
        NestServer.run(8080, new RestServer {
          def addRoutes {
            get("/hello", new RouteHandler {
              def exec(args: Map[String, String]): RouteResponse = new Utf8Response("world")
            })
          }
        })
      }
    }


The following shows how two domains can be hosted on port 80. The first, static.com, is a static content site that is hosted from embedded jar resources. The second, rest.com, is a service with REST handlers.  This service can easily be expanded to support both static and REST by extending StaticFileServer and adding the REST resources.


    package nest.router


    import _root_.io.viper.core.server.router._
    import io.viper.common.{NestServer, RestServer, StaticFileServer}
    import java.util.Map
    import org.json.JSONObject


    object Main {
      def main(args: Array[String]) {
        val handler = new HostRouterHandler

        // Serve static.com from cached jar resources in the static.com directory
        handler.putRoute("static.com", new StaticFileServer("res:///static.com/"))

        // Serve REST handlers
        handler.putRoute("rest.com", new RestServer {
          def addRoutes {

            get("/hello", new RouteHandler {
              def exec(args: Map[String, String]): RouteResponse = new Utf8Response("world")
            })

            get("/world", new RouteHandler {
              def exec(args: Map[String, String]): RouteResponse = {
                val json = new JSONObject()
                json.put("hello", "world")
                new JsonResponse(json)
              }
            })
          }
        })

        NestServer.run(handler)
      }
    }

### Maven

    <dependency>
      <groupId>io.viper</groupId>
      <artifactId>io.viper.core</artifactId>
      <version>0.1.1-SNAPSHOT</version>
    </dependency>
