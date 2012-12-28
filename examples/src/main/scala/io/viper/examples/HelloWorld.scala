package io.viper.examples

import io.viper.common.NestServer
import io.viper.core.server.router.Utf8Response


object HelloWorld extends NestServer(9080) {
  get("/hello") { args => new Utf8Response("world") }
}
