package viper.net.server.chunkproxy;


import java.util.EventListener;


public interface HttpChunkProxyEventListener extends EventListener
{
  void onProxyReady();
  void onProxyPaused();
  void onProxyCompleted();
  void onProxyError();
}
