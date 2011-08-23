package viper.net.server.router;


import org.jboss.netty.handler.codec.http.HttpRequest;


public class UriEndsWithRouteMatcher implements RouteMatcher
{
  private String route;

  public UriEndsWithRouteMatcher(String route)
  {
    this.route = route;
  }

  public boolean match(HttpRequest request)
  {
    return request.getUri().endsWith(route);
  }
}
