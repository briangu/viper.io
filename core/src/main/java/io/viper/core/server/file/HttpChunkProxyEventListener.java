package io.viper.core.server.file;


import java.util.EventListener;


public interface HttpChunkProxyEventListener extends EventListener
{
  void onProxyConnected();

  void onProxyWriteReady();
  void onProxyWritePaused();

  void onProxyCompleted();

  void onProxyError();
}
