package io.viper.core.server.file;


import java.util.Map;
import org.jboss.netty.channel.Channel;


public interface HttpChunkRelayEventListener
{
  void onError(Channel clientChannel);
  void onCompleted(Channel clientChannel);
  String onStart(Map<String, String> props);
}
