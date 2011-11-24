package io.viper.core.server.router;


import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;


public interface Route
{
  public String getRoute();

  public ChannelHandler getChannelHandler();

  public boolean isMatch(HttpRequest request);
}
