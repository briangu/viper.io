package io.viper.core.server.router;

import io.viper.core.server.security.AuthHandler;
import org.jboss.netty.handler.codec.http.HttpMethod;

public class GetRoute extends RestRoute
{
  public GetRoute(String route, RouteHandler handler)
  {
    this(route, handler, null);
  }

  public GetRoute(String route, RouteHandler handler, AuthHandler authHandler)
  {
    super(route, HttpMethod.GET, handler, authHandler);
  }
}
