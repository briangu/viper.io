viper.io
========

Viper.io is an http powertool written on top of Netty.  It's designed to simplify:

* Uploading and downloading of files directly to/from S3 using Netty
* Read-only websites (splash or product pages)
* REST application development (e.g., client-side templates that consume JSON)
* Dynamic thumbnail generation from images
* Virtual hosting (host multiple domains from the same process)

Maven
-----

    <dependency>
      <groupId>io.viper</groupId>
      <artifactId>io.viper.core</artifactId>
      <version>0.1.4</version>
    </dependency>

Example
-------

Hello World:

    import io.viper.common.{Response, NestServer}

    object HelloWorld extends NestServer(9080) {
      get("/hello") { args => Response("world") }
    }


Static content served from embedded resources:

    package io.viper.examples

    import io.viper.common.{NestServer, ViperServer}

    object StaticHost {
      def main(args: Array[String]) {
        NestServer.run(8080, new ViperServer("res:///static.com/"))
      }
    }


Serve files directly to/from S3:

    package io.viper.examples.files

    import io.viper.common.{ViperServer, NestServer}
    import io.viper.core.server.file.HttpChunkProxyHandler
    import io.viper.core.server.file.s3.{S3StaticFileServerHandler, S3StandardChunkProxy}


    object S3FileServer {
      def main(args: Array[String]) {
        var awsId = if (args.length > 2) args(0) else "awsId"
        var awsKey = if (args.length > 2) args(1) else "awsKey"
        var awsBucket = if (args.length > 2) args(2) else "awsBucket"

        NestServer.run(8080, new S3FileServer(awsId, awsKey, awsBucket, "localhost"))
      }
    }

    class S3FileServer(awsId: String, awsKey: String, awsBucket: String, downloadHostname: String) extends ViperServer("res:///s3server") {
      override def addRoutes {
        val proxy = new S3StandardChunkProxy(awsId, awsKey, awsBucket);
        val relayListener = new FileUploadChunkRelayEventListener(downloadHostname);
        addRoute(new HttpChunkProxyHandler("/u/", proxy, relayListener));

        addRoute(new S3StaticFileServerHandler("/d/$path", awsId, awsKey, awsBucket))
      }
    }


File Server:

    package io.viper.examples.files

    import io.viper.core.server.file.{StaticFileServerHandler, HttpChunkProxyHandler, FileChunkProxy}
    import io.viper.common.{StaticFileContentInfoProviderFactory, ViperServer, NestServer}


    object FileServer {
      def main(args: Array[String]) {
        NestServer.run(9080, new FileServer("/tmp/uploads", "localhost"))
      }
    }

    class FileServer(uploadFileRoot: String, downloadHostname: String) extends ViperServer("res:///fileserver") {
      override def addRoutes {
        val proxy = new FileChunkProxy(uploadFileRoot)
        val relayListener = new FileUploadChunkRelayEventListener(downloadHostname)
        addRoute(new HttpChunkProxyHandler("/u/", proxy, relayListener))
        val provider = StaticFileContentInfoProviderFactory.create(this.getClass, uploadFileRoot)
        get("/d/$path", new StaticFileServerHandler(provider))
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

            get("/echo/$something", new RouteHandler {
              def exec(args: Map[String, String]): RouteResponse = {
                val json = new JSONObject()
                json.put("hello", args.get("something"))
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
        <version>0.1.4</version>
    </dependency>
