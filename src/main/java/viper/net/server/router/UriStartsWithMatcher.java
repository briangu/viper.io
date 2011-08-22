package viper.net.server.router;


import org.jboss.netty.handler.codec.http.HttpRequest;


public class UriStartsWithMatcher implements Matcher
{
  private String route;

  public UriStartsWithMatcher(String route)
  {
    this.route = route;
  }

  public boolean match(HttpRequest request)
  {
    return request.getUri().startsWith(route);
  }
}
