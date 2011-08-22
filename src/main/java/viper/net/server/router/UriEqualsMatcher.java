package viper.net.server.router;


import org.jboss.netty.handler.codec.http.HttpRequest;


public class UriEqualsMatcher implements Matcher
{
  private String route;

  public UriEqualsMatcher(String route)
  {
    this.route = route;
  }

  public boolean match(HttpRequest request)
  {
    return request.getUri().equals(route);
  }
}
