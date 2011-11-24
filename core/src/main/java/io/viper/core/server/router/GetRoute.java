package io.viper.core.server.router;

import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class GetRoute implements Route
{
  public GetRoute(String s, RouteHandler handler)
  {
  }

  @Override
  public String getRoute() {
    return null;
  }

  @Override
  public ChannelHandler getChannelHandler()
  {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public boolean isMatch(HttpRequest request)
  {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
