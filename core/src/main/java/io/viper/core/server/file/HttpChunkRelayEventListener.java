package io.viper.core.server.file;


import java.util.Map;
import org.jboss.netty.channel.Channel;


public interface HttpChunkRelayEventListener
{
  // TODO: we should be able to pass the filekey to the onError method
  void onError(Channel clientChannel);
  void onCompleted(String fileKey, Channel clientChannel);
  String onStart(Map<String, String> props);
}
