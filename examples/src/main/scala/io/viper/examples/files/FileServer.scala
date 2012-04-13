package io.viper.examples.files


import io.viper.common.{ViperServer, NestServer}
import io.viper.core.server.file.{StaticFileServerHandler, ThumbnailFileContentInfoProvider, HttpChunkProxyHandler, FileChunkProxy}


object FileServer {
  def main(args: Array[String]) {
    NestServer.run(8080, new FileServer("/tmp/uploads", "localhost"))
  }
}

class FileServer(uploadFileRoot: String, downloadHostname: String) extends ViperServer("res://fileserver") {
  override def addRoutes {
    val proxy = new FileChunkProxy(uploadFileRoot);
    val relayListener = new FileUploadChunkRelayEventListener(downloadHostname);
    addRoute(new HttpChunkProxyHandler("/u/", proxy, relayListener));

    // add an on-demand thumbnail generation: it would be better to do this on file-add
    val thumbFileProvider = ThumbnailFileContentInfoProvider.create(uploadFileRoot, 640, 480);
    get("/thumb/$path", new StaticFileServerHandler(thumbFileProvider));
  }
}
