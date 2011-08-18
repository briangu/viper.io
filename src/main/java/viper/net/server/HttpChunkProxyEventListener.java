package viper.net.server;


import java.util.EventListener;


public interface HttpChunkProxyEventListener extends EventListener
{
  void onProxyReady();
  void onProxyPaused();
  void onProxyCompleted();
  void onProxyError();
}
