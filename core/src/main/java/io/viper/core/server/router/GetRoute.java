package io.viper.core.server.router;

import java.net.URI;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;

import java.util.List;
import java.util.Map;

import static org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer;
import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class GetRoute extends RestRoute
{
  public GetRoute(String route, RouteHandler handler)
  {
    super(route, handler, HttpMethod.GET);
  }
}
