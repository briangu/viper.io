package viper.net.server.chunkproxy;


import java.util.concurrent.ConcurrentHashMap;

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;


public class InsertOnlyFileContentInfoCache implements FileContentInfoProvider
{
  private final ConcurrentHashMap<String, FileContentInfo> _fileCache = new ConcurrentHashMap<String, FileContentInfo>();

  FileContentInfoProvider _infoProvider;

  public InsertOnlyFileContentInfoCache(FileContentInfoProvider infoProvider)
  {
    _infoProvider = infoProvider;
  }

  @Override
  public FileContentInfo getFileContent(String path)
  {
    FileContentInfo contentInfo;

    if (_fileCache.containsKey(path))
    {
      contentInfo = _fileCache.get(path);
    }
    else
    {
      synchronized (_fileCache)
      {
        if (!_fileCache.containsKey(path))
        {
          contentInfo = _infoProvider.getFileContent(path);
          if (contentInfo != null)
          {
            _fileCache.put(path, contentInfo);
          }
        }
        else
        {
          contentInfo = _fileCache.get(path);
        }
      }
    }

    return contentInfo;
  }
}
