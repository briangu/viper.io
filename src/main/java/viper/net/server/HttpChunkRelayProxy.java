package viper.net.server;


import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpChunk;


public interface HttpChunkRelayProxy
{
  public boolean isRelaying();
  public void init(HttpChunkRelayEventListener listener, MessageEvent e) throws Exception;
  public void appendChunk(HttpChunk chunk);
  public void complete(HttpChunk chunk);
  public void abort();
}
