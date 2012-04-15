package io.viper.core.server.router;

import java.util.Map;

public interface RouteHandler
{
  RouteResponse exec(Map<String, String> args) throws Exception;
}
