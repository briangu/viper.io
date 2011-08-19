package viper.net.server;


import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.UUID;
import javax.activation.MimetypesFileTypeMap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.base64.Base64;
import org.jboss.netty.handler.codec.base64.Base64Dialect;
import org.jboss.netty.util.CharsetUtil;


/**
 * Created by IntelliJ IDEA. User: bguarrac Date: 8/18/11 Time: 5:29 PM To change this template use File | Settings |
 * File Templates.
 */
public class Util
{
  static private MimetypesFileTypeMap _fileTypeMap = new MimetypesFileTypeMap();

  public static String getContentType(String filename)
  {
    if (filename == null || filename.length() == 0)
    {
      return "application/octet-stream";
    }

    String contentType;

    if (filename.endsWith(".js"))
    {
      contentType = "text/javascript";
    }
    else if (filename.endsWith(".css"))
    {
      contentType = "text/css";
    }
    else
    {
      contentType = _fileTypeMap.getContentType(filename);
    }

    return contentType;
  }

  public static String base64Encode(UUID uuid)
  {
    byte[] data = UUIDtoByteArray(uuid);
    ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(data);
    ChannelBuffer encoded = Base64.encode(buffer, Base64Dialect.URL_SAFE);
    return encoded.toString(CharsetUtil.US_ASCII).replace("=", "");
  }

  public static byte[] UUIDtoByteArray(UUID uuid)
  {
    ByteArrayOutputStream baos = null;
    DataOutputStream dos = null;
    try
    {
      baos = new ByteArrayOutputStream();
      dos = new DataOutputStream(baos);
      dos.writeLong(uuid.getLeastSignificantBits());
      dos.writeLong(uuid.getMostSignificantBits());
      dos.flush();
      byte[] data = baos.toByteArray();
      return data;
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }
    finally
    {
      try
      {
        if (dos != null) dos.close();
        if (baos != null) baos.close();
      }
      catch (IOException e)
      {
        e.printStackTrace();
      }
    }

    return null;
  }
}
