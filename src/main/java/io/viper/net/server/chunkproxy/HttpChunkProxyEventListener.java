package io.viper.net.server.chunkproxy;


import java.util.EventListener;


public interface HttpChunkProxyEventListener extends EventListener
{
  void onProxyConnected();

  void onProxyWriteReady();
  void onProxyWritePaused();

  void onProxyCompleted();

  void onProxyError();
}
