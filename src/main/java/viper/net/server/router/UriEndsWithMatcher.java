package viper.net.server.router;


import org.jboss.netty.handler.codec.http.HttpRequest;


public class UriEndsWithMatcher implements Matcher
{
  private String route;

  public UriEndsWithMatcher(String route)
  {
    this.route = route;
  }

  public boolean match(HttpRequest request)
  {
    return request.getUri().endsWith(route);
  }
}
