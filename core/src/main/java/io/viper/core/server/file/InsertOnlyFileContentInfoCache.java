package io.viper.core.server.file;


import java.util.concurrent.ConcurrentHashMap;


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

    if (path == null) return null;

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

  @Override
  public void dispose(FileContentInfo info)
  {
    // NOP
  }
}
