package viper.net.server.chunkproxy;


import java.nio.channels.FileChannel;
import org.jboss.netty.buffer.ChannelBuffer;


public class FileContentInfo
{
  public ChannelBuffer content;
  public FileChannel fileChannel;
  public String contentType;

  public FileContentInfo(FileChannel fileChannel, ChannelBuffer content, String contentType)
  {
    this.fileChannel = fileChannel;
    this.content = content;
    this.contentType = contentType;
  }
}

