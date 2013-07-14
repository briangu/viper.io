package io.viper.core.server.router;

import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;

public class StatusResponse extends RouteResponse
{
  public StatusResponse(HttpResponseStatus status)
  {
    HttpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
  }
}
