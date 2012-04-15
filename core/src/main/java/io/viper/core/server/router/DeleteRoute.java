package io.viper.core.server.router;


import org.jboss.netty.handler.codec.http.HttpMethod;


public class DeleteRoute extends RestRoute
{
  public DeleteRoute(String route, RouteHandler handler)
  {
    super(route, handler, HttpMethod.DELETE);
  }
}
