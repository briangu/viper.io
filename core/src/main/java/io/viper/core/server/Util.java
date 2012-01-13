package io.viper.core.server;


import io.viper.core.server.router.RouteResponse;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.activation.MimetypesFileTypeMap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.base64.Base64;
import org.jboss.netty.handler.codec.base64.Base64Dialect;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.util.CharsetUtil;
import io.viper.core.server.file.FileContentInfo;
import org.json.JSONException;
import org.json.JSONObject;
import static org.jboss.netty.buffer.ChannelBuffers.*;


public class Util
{
  static private MimetypesFileTypeMap _fileTypeMap = new MimetypesFileTypeMap();

  public static String getCurrentWorkingDirectory()
  {
    File directory = new File (".");
    String path = null;

    try
    {
      path = directory.getCanonicalPath();
    }
    catch(Exception e)
    {
      e.printStackTrace();
    }

    return path;
  }

  public static Map<String, FileContentInfo> createFileMap(String rootPath)
    throws IOException, JSONException
  {
    String metaFilePath = rootPath + File.separatorChar + ".meta";

    return createFileMap(rootPath, metaFilePath);
  }

  public static Map<String, FileContentInfo> createFileMap(String rootPath, String metaFilePath)
    throws IOException, JSONException
  {
    Map<String, FileContentInfo> fileMap = new ConcurrentHashMap<String, FileContentInfo>();

    Stack<String> stack = new Stack<String>();
    stack.push(rootPath);

    while(!stack.isEmpty())
    {
      String path = stack.pop();

      File dir = new File(path);

      if (!dir.isDirectory()) continue;

      String relativePath = path.substring(rootPath.length());
      if (!relativePath.endsWith("/"))
      {
        relativePath += "/";
      }
      FileContentInfo defaultFile = getDefaultFile(path);
      if (defaultFile != null)
      {
        fileMap.put(relativePath, defaultFile);
      }

      File[] files = dir.listFiles();

      for (File subFile : files)
      {
        if (subFile.isDirectory())
        {
          stack.push(subFile.getAbsolutePath());
          continue;
        }
        else if (subFile.isFile())
        {
          File metaFile = new File(metaFilePath + path);
          JSONObject jsonObject = getMetaInfo(metaFile);

          Map<String, String> meta = new HashMap<String, String>();

          Iterator<String> keys = jsonObject.keys();
          while(keys.hasNext())
          {
            String key = keys.next();
            meta.put(key, jsonObject.getString(key));
          }

          if (!meta.containsKey(HttpHeaders.Names.CONTENT_TYPE))
          {
            meta.put(HttpHeaders.Names.CONTENT_TYPE, Util.getContentType(subFile.getPath()));
          }

          fileMap.put(
            subFile.getPath().substring(rootPath.length()),
            FileContentInfo.create(subFile, meta));
        }
      }
    }

    return fileMap;
  }

  public static JSONObject getMetaInfo(File file)
    throws IOException, JSONException
  {
    JSONObject result;

    if (file.exists())
    {
      RandomAccessFile metaRaf = new RandomAccessFile(file, "r");
      String rawData = metaRaf.readUTF();
      result = new JSONObject(rawData);
      metaRaf.close();
    }
    else
    {
      result = new JSONObject();
    }

    return result;
  }

  public static FileContentInfo getDefaultFileFromResources(java.lang.Class<?> clazz, String rootPath)
      throws IOException
  {
    File foundIndex = null;

    final String[] defaultFiles = new String[]{"index.html", "index.htm"};

    for (String defaultFile : defaultFiles)
    {
      URL url = clazz.getResource(rootPath + File.separatorChar + defaultFile);
      if (url == null)
      {
        continue;
      }
      File file = new File(url.getFile());
      if (file.exists())
      {
        foundIndex = file;
        break;
      }
    }

    FileContentInfo result = null;

    if (foundIndex != null)
    {
      Map<String, String> meta = new HashMap<String, String>();
      meta.put(HttpHeaders.Names.CONTENT_TYPE, "text/html");

      FileChannel fc = new RandomAccessFile(foundIndex, "r").getChannel();
      ByteBuffer roBuf = fc.map(FileChannel.MapMode.READ_ONLY, 0, (int) fc.size());
      result = new FileContentInfo(fc, ChannelBuffers.wrappedBuffer(roBuf), meta);
    }

    return result;
  }

  public static FileContentInfo getDefaultFile(String rootPath)
      throws IOException
  {
    File foundIndex = null;

    final String[] defaultFiles = new String[]{"index.html", "index.htm"};

    for (String defaultFile : defaultFiles)
    {
      File file = new File(rootPath + File.separatorChar + defaultFile);
      if (file.exists())
      {
        foundIndex = file;
        break;
      }
    }

    FileContentInfo result = null;

    if (foundIndex != null)
    {
      Map<String, String> meta = new HashMap<String, String>();
      meta.put(HttpHeaders.Names.CONTENT_TYPE, "text/html");

      FileChannel fc = new RandomAccessFile(foundIndex, "r").getChannel();
      ByteBuffer roBuf = fc.map(FileChannel.MapMode.READ_ONLY, 0, (int) fc.size());
      result = new FileContentInfo(fc, ChannelBuffers.wrappedBuffer(roBuf), meta);
    }

    return result;
  }



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
    if (uri.contains(File.separator + ".") || uri.contains("." + File.separator) || uri.startsWith(".") || uri.endsWith(
        "."))
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
    response.setContent(wrappedBuffer(obj.toString().getBytes()));
    return new RouteResponse(response);
  }
}
