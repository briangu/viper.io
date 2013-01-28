package io.viper.core.server.router;


import io.viper.core.server.security.AuthHandler;
import org.jboss.netty.handler.codec.http.HttpMethod;


public class DeleteRoute extends RestRoute
{
  public DeleteRoute(String route, RouteHandler handler)
  {
    this(route, handler, null);
  }

  public DeleteRoute(String route, RouteHandler handler, AuthHandler authHandler)
  {
    super(route, HttpMethod.DELETE, handler, authHandler);
  }
}
