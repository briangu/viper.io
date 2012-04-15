package io.viper.core.server.router;

import org.jboss.netty.handler.codec.http.HttpMethod;

public class GetRoute extends RestRoute
{
  public GetRoute(String route, RouteHandler handler)
  {
    super(route, handler, HttpMethod.GET);
  }
}
