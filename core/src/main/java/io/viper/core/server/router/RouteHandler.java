package io.viper.core.server.router;

import org.jboss.netty.handler.codec.http.HttpResponse;

import java.util.Map;

public interface RouteHandler
{
  HttpResponse exec(Map<String, String> args) throws Exception;
}
