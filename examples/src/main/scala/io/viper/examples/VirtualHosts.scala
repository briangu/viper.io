package io.viper.examples


import io.viper.common._
import org.json.JSONObject
import java.util
import io.viper.core.server.router.RouteResponse

// curl -H 'Host: rest.com:8080' http://localhost:8080/hello
object VirtualHosts extends MultiHostServer(8080) {

  // Serve static.com from cached jar resources in the static.com directory
  route("static.com", "res:///static.com/")

  // Serve REST handlers
  route("rest.com", "res:///rest.com", { server =>
    server.get("/hello") { args => Response("world") }
    server.get("/echo/$something") { args =>
      val json = new JSONObject()
      json.put("response", args.get("something"))
      Response(json)
    }
  })
}
