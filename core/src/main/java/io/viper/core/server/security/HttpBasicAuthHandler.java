package io.viper.core.server.security;

import com.amazon.thirdparty.Base64;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;

public abstract class HttpBasicAuthHandler implements AuthHandler
{
  @Override
  public boolean isAuthorized(HttpRequest request)
  {
    try {
      String authorization = request.getHeader(HttpHeaders.Names.AUTHORIZATION).replaceAll("Basic ", "");
      String[] credentials = new String(Base64.decode(authorization)).split(":");
      String username = credentials[0];
      String password = credentials[1];
      return isAuthorized(request, username, password);
    } catch (Exception e) {
      return false;
    }
  }

  public abstract boolean isAuthorized(HttpRequest request, String username, String password);
}
