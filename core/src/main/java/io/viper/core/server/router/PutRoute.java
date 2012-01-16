package io.viper.core.server.router;


import org.jboss.netty.handler.codec.http.HttpMethod;


public class PutRoute extends RestRoute
{
  public PutRoute(String route, RouteHandler handler)
  {
    super(route, handler, HttpMethod.PUT);
  }
}
