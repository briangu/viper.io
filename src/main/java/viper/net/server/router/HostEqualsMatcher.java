package viper.net.server.router;


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
    if (request.containsHeader("Host"))
    {
      return request.getHeader("Host").equals(_host);
    }
    else if (request.containsHeader("host"))
    {
      return request.getHeader("host").equals(_host);
    }

    return false;
  }
}
