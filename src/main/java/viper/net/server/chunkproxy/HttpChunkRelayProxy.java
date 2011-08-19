package viper.net.server.chunkproxy;


import org.jboss.netty.handler.codec.http.HttpChunk;


public interface HttpChunkRelayProxy
{
  public boolean isRelaying();
  public void init(HttpChunkProxyEventListener listener, String objectName, long objectSize, String contentType) throws Exception;
  public void appendChunk(HttpChunk chunk);
  public void complete(HttpChunk chunk);
  public void abort();
}
