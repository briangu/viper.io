package io.viper.examples

import io.viper.common.{Response, NestServer}


object HelloWorld extends NestServer(9080) {
  get("/hello") { args => Response("world") }
}
