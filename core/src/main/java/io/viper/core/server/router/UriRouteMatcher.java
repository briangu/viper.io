package io.viper.core.server.router;


import org.jboss.netty.handler.codec.http.HttpRequest;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;


public class UriRouteMatcher implements RouteMatcher
{
  private String _route;
  private MatchMode _mode;

  public enum MatchMode
  {
    startsWith,
    equals,
    endsWith,
//    regex
  }

  public String getRoute()
  {
    return _route;
  }

  public UriRouteMatcher(MatchMode mode, String route)
  {
    _mode = mode;
    _route = route;
  }

  public boolean match(HttpRequest request)
  {
    switch (_mode)
    {
      case startsWith: return request.getUri().startsWith(_route);
      case equals: return request.getUri().equals(_route);
      case endsWith: return request.getUri().endsWith(_route);
    }

    throw new NotImplementedException();
  }
}
