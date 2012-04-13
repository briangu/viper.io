package io.viper.examples.files


import io.viper.core.server.file.HttpChunkRelayEventListener
import io.viper.core.server.Util
import org.jboss.netty.handler.codec.http.{HttpResponseStatus, HttpVersion, DefaultHttpResponse}
import org.jboss.netty.buffer.ChannelBuffers
import org.json.{JSONException, JSONObject}
import java.io.UnsupportedEncodingException
import org.jboss.netty.channel.{Channel, ChannelFutureListener}
import java.util.{Map, UUID}


class FileUploadChunkRelayEventListener(hostname: String) extends HttpChunkRelayEventListener {
  def onError(clientChannel: Channel) {
    if (clientChannel != null) sendResponse(null, clientChannel, false)
  }

  def onCompleted(fileKey: String, clientChannel: Channel) = sendResponse(fileKey, clientChannel, true)

  def onStart(props: Map[String, String]) = Util.base64Encode(UUID.randomUUID())

  private def sendResponse(fileKey: String, clientChannel: Channel, success: Boolean) {
    try {
      val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

      val jsonResponse = new JSONObject()
      jsonResponse.put("success", success)
      if (success) {
        jsonResponse.put("thumbnail", String.format("%s/thumb/%s", hostname, fileKey))
        jsonResponse.put("url", String.format("/d/%s", fileKey))
        jsonResponse.put("key", fileKey)
      }

      response.setContent(ChannelBuffers.wrappedBuffer(jsonResponse.toString(2).getBytes("UTF-8")))
      clientChannel.write(response).addListener(ChannelFutureListener.CLOSE)
    }
    catch {
      case e: JSONException => e.printStackTrace()
      case e: UnsupportedEncodingException => e.printStackTrace()
    }
  }
}
