package io.viper.examples.files



import io.viper.common.{StaticFileContentInfoProviderFactory, ViperServer, NestServer}
import io.viper.core.server.file.{StaticFileServerHandler, HttpChunkProxyHandler}
import io.viper.core.server.file.s3.{S3StaticFileServerHandler, S3StandardChunkProxy}


object S3FileServer {
  def main(args: Array[String]) {
    var awsId = if (args.length > 2) args(0) else "awsId"
    var awsKey = if (args.length > 2) args(1) else "awsKey"
    var awsBucket = if (args.length > 2) args(2) else "awsBucket"

    NestServer.run(8080, new S3FileServer(awsId, awsKey, awsBucket, "localhost"))
  }
}

class S3FileServer(awsId: String, awsKey: String, awsBucket: String, downloadHostname: String) extends ViperServer("res:///s3server") {
  override def addRoutes {
    val proxy = new S3StandardChunkProxy(awsId, awsKey, awsBucket);
    val relayListener = new FileUploadChunkRelayEventListener(downloadHostname);
    addRoute(new HttpChunkProxyHandler("/u/", proxy, relayListener));

    // TODO: add caching layer
    addRoute(new S3StaticFileServerHandler("/d/$path", awsId, awsKey, awsBucket))
  }
}
