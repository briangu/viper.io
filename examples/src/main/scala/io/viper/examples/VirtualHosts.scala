package io.viper.examples


import io.viper.common._
import org.json.JSONObject

// curl -H 'Host: rest.com:8080' http://localhost:8080/hello
object VirtualHosts extends MultiHostServer(8080) {

  // Serve static.com from cached jar resources in the static.com directory
  route("static.com") // uses res:///static.com by default
  route("static2.com", "res://static.com")
  route(VirtualServer("foo.com"))
  route(VirtualServer("bar.com", "res:///foo.com"))

  // Serve REST handlers
  route("rest.com", { server =>
    server.get("/hello") { args => Response("world") }
    server.get("/echo/$something") { args =>
      val json = new JSONObject()
      json.put("response", args.get("something"))
      Response(json)
    }
  })
}
