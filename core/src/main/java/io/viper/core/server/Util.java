package io.viper.core.server;


import io.viper.core.server.router.RouteResponse;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.base64.Base64;
import org.jboss.netty.handler.codec.base64.Base64Dialect;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.jboss.netty.util.CharsetUtil;
import org.json.JSONException;
import org.json.JSONObject;

import javax.activation.MimetypesFileTypeMap;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.UUID;

import static org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer;


public class Util
{
  static private MimetypesFileTypeMap _fileTypeMap = new MimetypesFileTypeMap();

  public static byte[] copyStream(InputStream is) throws IOException
  {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    int nRead;
    byte[] data = new byte[64 * 1024];
    while ((nRead = is.read(data, 0, data.length)) != -1) {
      buffer.write(data, 0, nRead);
    }
    buffer.flush();

    return buffer.toByteArray();
  }

  public static String getContentType(String filename)
  {
    if (filename == null || filename.length() == 0)
    {
      return "application/octet-stream";
    }

    String contentType;

    if (filename.endsWith(".html") || filename.endsWith(".htm")) {
      contentType = "text/html";
    }
    else if (filename.endsWith(".js"))
    {
      contentType = "text/javascript";
    }
    else if (filename.endsWith(".css"))
    {
      contentType = "text/css";
    }
    else if (filename.endsWith(".png"))
    {
      contentType = "image/png";
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

  public static String sanitizeUri(String uri)
      throws URISyntaxException
  {
    // Decode the path.
    try
    {
      uri = URLDecoder.decode(uri, "UTF-8");
    }
    catch (UnsupportedEncodingException e)
    {
      try
      {
        uri = URLDecoder.decode(uri, "ISO-8859-1");
      }
      catch (UnsupportedEncodingException e1)
      {
        throw new Error();
      }
    }

    // Convert file separators.
    uri = uri.replace(File.separatorChar, '/');

    // Simplistic dumb security check.
    // You will have to do something serious in the production environment.
    if (uri.contains(File.separator + ".") || uri.contains("." + File.separator) || uri.startsWith(".") || uri.endsWith("."))
    {
      return null;
    }

    QueryStringDecoder decoder = new QueryStringDecoder(uri);
    uri = decoder.getPath();

    return uri;
  }

  public static JSONObject createJson(Object... args) throws JSONException
  {
    if (args.length %2 != 0) throw new IllegalArgumentException("missing last value: args require key/value pairs");

    JSONObject obj = new JSONObject();

    for (int i = 0; i < args.length; i += 2)
    {
      obj.put(args[i].toString(), args[i+1]);
    }

    return obj;
  }

  public static RouteResponse createJsonResponse(Object... args) throws JSONException
  {
    return Util.createJsonResponse(createJson(args));
  }

  public static RouteResponse createJsonResponse(JSONObject obj)
  {
    DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    response.setHeader("Content-Type", "application/json");
    response.setContent(wrappedBuffer(obj.toString().getBytes()));
    return new RouteResponse(response);
  }
}
