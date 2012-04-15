package io.viper.examples

import io.viper.common.{NestServer, ViperServer}

object StaticHost {
  def main(args: Array[String]) {
    NestServer.run(8080, new ViperServer("res:///static.com/"))
  }
}
