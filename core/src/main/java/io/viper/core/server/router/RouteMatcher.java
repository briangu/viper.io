package io.viper.core.server.router;


import org.jboss.netty.handler.codec.http.HttpRequest;


public interface RouteMatcher
{
  public String getRoute();

  public boolean match(HttpRequest request);
}
