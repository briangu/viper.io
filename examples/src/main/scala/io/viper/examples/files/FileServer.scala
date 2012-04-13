package io.viper.examples.files


import io.viper.core.server.file.{HttpChunkProxyHandler, FileChunkProxy}
import io.viper.common.{ViperServer, NestServer}

object Main {
  def main(args: Array[String]) {
    NestServer.run(8080, new FileServer("/tmp/uploads", "localhost"))
  }
}

class FileServer(uploadFileRoot: String, downloadHostname: String) extends ViperServer("res://fileserver") {
  override def addRoutes {
    val proxy = new FileChunkProxy(uploadFileRoot);
    val relayListener = new FileUploadChunkRelayEventListener(downloadHostname);
    addRoute(new HttpChunkProxyHandler("/u/", proxy, relayListener));
  }
}
