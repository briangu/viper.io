package io.viper.core.server.router;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.*;

import java.io.UnsupportedEncodingException;

public class Utf8Response extends RouteResponse
{
  public Utf8Response(String val) throws UnsupportedEncodingException
  {
    HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
    response.setContent(ChannelBuffers.wrappedBuffer(val.getBytes("UTF-8")));
    HttpResponse = response;
  }
}
