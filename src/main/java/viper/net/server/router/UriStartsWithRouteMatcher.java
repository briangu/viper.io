package viper.net.server.router;


import org.jboss.netty.handler.codec.http.HttpRequest;


public class UriStartsWithRouteMatcher implements RouteMatcher
{
  private String route;

  public UriStartsWithRouteMatcher(String route)
  {
    this.route = route;
  }

  public boolean match(HttpRequest request)
  {
    return request.getUri().startsWith(route);
  }
}
