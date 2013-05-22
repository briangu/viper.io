package io.viper.examples

import io.viper.common._

// curl -H 'Host: helloworld.com:8080' http://localhost:8080/hello
class DynamicHelloWorld extends VirtualServer("helloworld.com") {
  get("/hello", { args => Response("world") })
}

object DynamicServer extends DynamicContainerApp(8080, ".")