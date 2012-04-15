package io.viper.core.server.router;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.*;

import java.io.UnsupportedEncodingException;

public class StatusResponse extends RouteResponse
{
  public StatusResponse(HttpResponseStatus status)
  {
    HttpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
  }
}
