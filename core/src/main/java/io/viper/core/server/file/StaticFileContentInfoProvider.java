package io.viper.core.server.file;


import io.viper.core.server.Util;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.json.JSONException;
import org.json.JSONObject;


public class StaticFileContentInfoProvider implements FileContentInfoProvider
{
  private String _rootPath;
  private final boolean _fromClasspath;
  String _metaFilePath;

  public static StaticFileContentInfoProvider create(String rootPath)
  {
    return new StaticFileContentInfoProvider(rootPath);
  }

  public StaticFileContentInfoProvider(String rootPath)
  {
    _fromClasspath = rootPath.startsWith("classpath://");

    if (_fromClasspath)
    {
      _rootPath = rootPath.replace("classpath://", "");
      if (_rootPath.lastIndexOf("/") == _rootPath.length() - 1)
      {
        _rootPath = _rootPath.substring(0, _rootPath.length() - 1);
      }
    }
    else
    {
      _rootPath = rootPath;
    }

    _rootPath = _rootPath.replace(File.separatorChar, '/');
    _metaFilePath = _rootPath + File.separatorChar + ".meta" + File.separatorChar;
  }

  @Override
  public FileContentInfo getFileContent(String path)
  {
    FileChannel fc = null;
    FileContentInfo result = null;

    // TODO: make a touch more secure - chroot to the rescue!
    try
    {
      final String fullPath = _rootPath + path;

      if (fullPath.endsWith("/"))
      {
        result =
            _fromClasspath
              ? Util.getDefaultFileFromResources(this.getClass(), fullPath)
              : Util.getDefaultFile(fullPath);
      }
      else
      {
        File file;

        if (_fromClasspath)
        {
          URL url = this.getClass().getResource(fullPath);
          if (url == null)
          {
            return null;
          }
          file = new File(url.getFile());
        }
        else
        {
          file = new File(fullPath);
        }

        if (file.exists())
        {
          Map<String, String> meta = new HashMap<String, String>();

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
    if (info.fileChannel != null)
    {
      try
      {
        info.fileChannel.close();
        info.content.clear();
      }
      catch (IOException e)
      {
        e.printStackTrace();
      }
    }
  }
}
