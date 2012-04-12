package io.viper.core.server.router;


import java.io.UnsupportedEncodingException;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.json.JSONObject;


public class JsonResponse extends RouteResponse
{
  public JsonResponse(JSONObject val) throws UnsupportedEncodingException
  {
    HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/javascript; charset=UTF-8");
    response.setContent(ChannelBuffers.wrappedBuffer(val.toString().getBytes("UTF-8")));
    HttpResponse = response;
  }
}
