package io.viper.net.server.chunkproxy;


import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Map;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.json.JSONObject;


public class FileContentInfo
{
  public ChannelBuffer content;
  public FileChannel fileChannel;
  public Map<String, String> meta;

  public FileContentInfo(FileChannel fileChannel, ChannelBuffer content, Map<String, String> meta)
  {
    this.fileChannel = fileChannel;
    this.content = content;
    this.meta = meta;
  }

  public static FileContentInfo create(File file, Map<String, String> meta)
    throws IOException
{
    FileChannel fc = new RandomAccessFile(file, "r").getChannel();
    ByteBuffer roBuf = fc.map(FileChannel.MapMode.READ_ONLY, 0, (int) fc.size());
    FileContentInfo result = new FileContentInfo(fc, ChannelBuffers.wrappedBuffer(roBuf), meta);
    return result;
  }
}

