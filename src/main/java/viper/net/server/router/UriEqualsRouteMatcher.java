package viper.net.server.router;


import org.jboss.netty.handler.codec.http.HttpRequest;


public class UriEqualsRouteMatcher implements RouteMatcher
{
  private String route;

  public UriEqualsRouteMatcher(String route)
  {
    this.route = route;
  }

  public boolean match(HttpRequest request)
  {
    return request.getUri().equals(route);
  }
}
