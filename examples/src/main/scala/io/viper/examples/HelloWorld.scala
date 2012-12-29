package io.viper.examples

import io.viper.common.{VirtualServer, Response, NestServer}

// curl http://localhost:8080/hello
object HelloWorld extends NestServer(9080) {
  get("/hello") { args => Response("world") }
}

