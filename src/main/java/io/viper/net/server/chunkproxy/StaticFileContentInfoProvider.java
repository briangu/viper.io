package io.viper.net.server.chunkproxy;


import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.channels.FileChannel;
import io.viper.net.server.Util;


public class StaticFileContentInfoProvider implements FileContentInfoProvider
{
  private String _rootPath;
  private final boolean _fromClasspath;
  String _metaFilePath;

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
          String contentType;

          File meta = new File(_metaFilePath + path);
          if (meta.exists())
          {
            RandomAccessFile metaRaf = new RandomAccessFile(meta, "r");
            contentType = metaRaf.readUTF();
            metaRaf.close();
          }
          else
          {
            contentType = Util.getContentType(path);
          }

          result = FileContentInfo.create(file, contentType);
        }
      }
    }
    catch (IOException e)
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
}
