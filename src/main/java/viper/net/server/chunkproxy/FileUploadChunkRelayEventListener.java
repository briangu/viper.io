package viper.net.server.chunkproxy;


import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.UUID;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.json.JSONException;
import org.json.JSONObject;
import viper.net.server.Util;


public class FileUploadChunkRelayEventListener implements HttpChunkRelayEventListener
{
  String _fileKey;

  public void onError(Channel clientChannel)
  {
    sendResponse(clientChannel, false);
  }

  public void onCompleted(Channel clientChannel)
  {
    sendResponse(clientChannel, true);
  }

  @Override
  public String onStart(Map<String, String> props)
  {
    _fileKey = Util.base64Encode(UUID.randomUUID());
    return _fileKey;
  }

  private void sendResponse(Channel clientChannel, boolean success)
  {
    try
    {
      HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
      JSONObject jsonResponse = new JSONObject();
      jsonResponse.put("success", Boolean.toString(success));
      jsonResponse.put("url", String.format("http://localhost:8080/uploads/%s", _fileKey));
      response.setContent(ChannelBuffers.wrappedBuffer(jsonResponse.toString(2).getBytes("UTF-8")));
      clientChannel.write(response).addListener(ChannelFutureListener.CLOSE);
    }
    catch (JSONException e)
    {
      e.printStackTrace();
    }
    catch (UnsupportedEncodingException e)
    {
      e.printStackTrace();
    }
  }
}
