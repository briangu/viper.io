package viper.net.server;


import org.jboss.netty.channel.Channel;


public interface HttpChunkRelayEventListener
{
  void onError(Channel clientChannel);
  void onCompleted(Channel clientChannel);
}
