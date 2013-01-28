package io.viper.core.server.router;


import io.viper.core.server.security.AuthHandler;
import org.jboss.netty.handler.codec.http.HttpMethod;


public class PutRoute extends RestRoute
{
  public PutRoute(String route, RouteHandler handler)
  {
    this(route, handler, null);
  }

  public PutRoute(String route, RouteHandler handler, AuthHandler authHandler)
  {
    super(route, HttpMethod.PUT, handler, authHandler);
  }
}
