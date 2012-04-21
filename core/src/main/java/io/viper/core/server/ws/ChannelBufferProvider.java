package io.viper.core.server.ws;

import org.jboss.netty.buffer.ChannelBuffer;

public interface ChannelBufferProvider {
  public ChannelBuffer handle(String webSocketLocation);
}
