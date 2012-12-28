package io.viper.examples

import io.viper.common.{RestServer, NestServer}
import io.viper.core.server.router.Utf8Response
import RestServer._


object HelloWorld extends App {
  NestServer.run(9080) { server => get(server, "/hello") { args => new Utf8Response("world") } }
}
