package viper.net.server.chunkproxy;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;


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

  public static FileContentInfo create(File file, String contentType)
    throws IOException
{
    FileChannel fc = new RandomAccessFile(file, "r").getChannel();
    ByteBuffer roBuf = fc.map(FileChannel.MapMode.READ_ONLY, 0, (int) fc.size());
    FileContentInfo result = new FileContentInfo(fc, ChannelBuffers.wrappedBuffer(roBuf), contentType);
    return result;
  }
}

