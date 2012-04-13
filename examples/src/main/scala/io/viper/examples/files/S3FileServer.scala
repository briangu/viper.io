package io.viper.examples.files



import io.viper.core.server.file.{HttpChunkProxyHandler}
import io.viper.common.{ViperServer, NestServer}
import io.viper.core.server.file.s3.S3StandardChunkProxy


object S3FileServer {
  def main(args: Array[String]) {
    NestServer.run(8080, new S3FileServer("awsid", "awskey", "awsBucket", "localhost"))
  }
}

class S3FileServer(awsId: String, awsKey: String, awsBucket: String, downloadHostname: String) extends ViperServer("res:///s3server") {
  override def addRoutes {
    val proxy = new S3StandardChunkProxy(awsId, awsKey, awsBucket);
    val relayListener = new FileUploadChunkRelayEventListener(downloadHostname);
    addRoute(new HttpChunkProxyHandler("/u/", proxy, relayListener));
  }
}
