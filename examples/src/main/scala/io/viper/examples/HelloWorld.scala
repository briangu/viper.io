package io.viper.examples

import io.viper.common.{Response, NestServer}

// curl http://localhost:8080/hello
object HelloWorld extends NestServer(8080) {
  get("/hello") { args => Response("world") }
}

