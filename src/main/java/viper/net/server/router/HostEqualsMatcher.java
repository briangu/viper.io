package viper.net.server.router;


import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;


public class HostEqualsMatcher implements Matcher
{
  private String _host;

  public HostEqualsMatcher(String host)
  {
    _host = host;
  }

  public boolean match(HttpRequest request)
  {
    if (request.containsHeader(HttpHeaders.Names.HOST))
    {
      return request.getHeader(HttpHeaders.Names.HOST).equals(_host);
    }

    return false;
  }
}
