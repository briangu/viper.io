package io.viper.core.server.file;


import io.viper.core.server.Util;

import java.io.*;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.json.JSONException;
import org.json.JSONObject;


public class StaticFileContentInfoProvider implements FileContentInfoProvider
{
  private String _rootPath;
  private final boolean _fromClasspath;
  String _metaFilePath;
  private Class _clazz;

  final String[] defaultFiles = new String[]{"index.html", "index.htm"};

  public static StaticFileContentInfoProvider create(Class clazz, String rootPath)
  {
    return new StaticFileContentInfoProvider(clazz, rootPath);
  }

  public StaticFileContentInfoProvider(Class clazz, String rootPath)
  {
    _clazz = clazz;

    _fromClasspath = rootPath.startsWith("classpath://");
    if (_fromClasspath)
    {
      rootPath = rootPath.replace("classpath://", "");
    }

    _rootPath = rootPath.endsWith("/") ? rootPath : rootPath + "/";

    _rootPath = _rootPath.replace(File.separatorChar, '/');
    _metaFilePath = _rootPath + File.separatorChar + ".meta" + File.separatorChar;
  }

  @Override
  public FileContentInfo getFileContent(String path)
  {
    FileChannel fc = null;
    FileContentInfo result = null;

    try
    {
      final String fullPath = _rootPath + path;

      if (fullPath.endsWith("/"))
      {
        for (String defaultFileName : defaultFiles)
        {
          result = getFileContent(path + defaultFileName);
          if (result != null) break;
        }
      }
      else
      {
        Map<String, String> meta = new HashMap<String, String>();

        File file;

        if (_fromClasspath)
        {
          URL url = _clazz.getResource(fullPath);
          if (url == null)
          {
            return null;
          }

          if (url.toString().startsWith("jar:")) {
            // TODO: this is a hack to deal with the fact that jarFile seems to be non-thread safe
            byte[] bytes;
            synchronized (StaticFileContentInfoProvider.class) {
              JarURLConnection conn = (JarURLConnection)url.openConnection();
              JarFile jarFile = conn.getJarFile();
              InputStream input = jarFile.getInputStream(conn.getJarEntry());
              bytes = Util.copyStream(input);
              jarFile.close();
            }
            meta.put(HttpHeaders.Names.CONTENT_TYPE, Util.getContentType(path));
            meta.put(HttpHeaders.Names.CONTENT_LENGTH, Long.toString(bytes.length));
            result = FileContentInfo.create(bytes, meta);
            file = null;
          }
          else
          {
            file = new File(url.getFile());
          }
        }
        else
        {
          file = new File(fullPath);
        }

        if (file != null && file.exists())
        {
          File metaFile = new File(_metaFilePath + path);
          if (metaFile.exists())
          {
            RandomAccessFile metaRaf = new RandomAccessFile(metaFile, "r");
            String rawJSON = metaRaf.readUTF();
            JSONObject jsonObject = new JSONObject(rawJSON);
            metaRaf.close();

            Iterator<String> keys = jsonObject.keys();
            while(keys.hasNext())
            {
              String key = keys.next();
              meta.put(key, jsonObject.getString(key));
            }
          }
          else
          {
            meta.put(HttpHeaders.Names.CONTENT_TYPE, Util.getContentType(path));
            meta.put(HttpHeaders.Names.CONTENT_LENGTH, Long.toString(file.length()));
          }

          result = FileContentInfo.create(file, meta);
        }
      }
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }
    catch (JSONException e)
    {
      e.printStackTrace();
    }
    finally
    {
      if (result == null)
      {
        if (fc != null)
        {
          try
          {
            fc.close();
          }
          catch (IOException e)
          {
            e.printStackTrace();
          }
        }
      }
    }

    return result;
  }

  @Override
  public void dispose(FileContentInfo info)
  {
    info.dispose();
  }
}
