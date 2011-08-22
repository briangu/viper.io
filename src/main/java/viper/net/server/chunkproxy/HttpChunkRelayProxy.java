package viper.net.server.chunkproxy;


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

  public void appendChunk(HttpChunk chunk);

  public void complete(HttpChunk chunk);

  public void abort();

}

