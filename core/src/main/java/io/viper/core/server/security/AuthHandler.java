package io.viper.core.server.security;

import org.jboss.netty.handler.codec.http.HttpRequest;

public interface AuthHandler
{
  public boolean isAuthorized(HttpRequest request);
}
