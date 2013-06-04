package io.viper.examples.files


import io.viper.core.server.file.{StaticFileServerHandler, HttpChunkProxyHandler, FileChunkProxy}
import io.viper.common.{StaticFileContentInfoProviderFactory, ViperServer, NestServer}


object FileServer {
  def main(args: Array[String]) {
    NestServer.run(9080, new FileServer("/tmp/uploads", "localhost"))
  }
}

class FileServer(uploadFileRoot: String, downloadHostname: String) extends ViperServer("res:///fileserver") {
  override def addRoutes() {
    val proxy = new FileChunkProxy(uploadFileRoot)
    val relayListener = new FileUploadChunkRelayEventListener(downloadHostname)
    addRoute(new HttpChunkProxyHandler("/u/", proxy, relayListener))

    val provider = StaticFileContentInfoProviderFactory.create(this.getClass, uploadFileRoot)
    get("/d/$path", new StaticFileServerHandler(provider))
  }
}
