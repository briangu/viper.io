package io.viper.examples


import io.viper.common._
import org.json.JSONObject


object VirtualHosts extends MultiHostServer(8080) {

  // Serve static.com from cached jar resources in the static.com directory
  route("static.com", "res:///static.com/")

  // Serve REST handlers
  route("rest.com", new ViperServer {
    get("/hello") { args => Response("world") }
    get("/echo/$something") { args =>
      val json = new JSONObject()
      json.put("response", args.get("something"))
      Response(json)
    }
  })
}
