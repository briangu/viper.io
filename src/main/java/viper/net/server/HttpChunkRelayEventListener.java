package viper.net.server;


import java.util.EventListener;


public interface HttpChunkRelayEventListener extends EventListener
{
  void onProxyReady();
  void onProxyPaused();
  void onProxyError();
}
