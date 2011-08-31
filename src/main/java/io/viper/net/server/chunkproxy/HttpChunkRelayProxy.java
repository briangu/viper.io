package io.viper.net.server.chunkproxy;


import java.util.Map;
import org.jboss.netty.handler.codec.http.HttpChunk;


public interface HttpChunkRelayProxy
{
  public boolean isRelaying();

  public void init(
    HttpChunkProxyEventListener listener,
    String objectName,
    Map<String, String> meta,
    long objectSize) throws Exception;

  public void writeChunk(HttpChunk chunk);

  public void abort();

}

