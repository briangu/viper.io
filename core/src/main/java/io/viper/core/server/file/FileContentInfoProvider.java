package io.viper.core.server.file;


public interface FileContentInfoProvider
{
  FileContentInfo getFileContent(String rootPath);
  void dispose(FileContentInfo info);
}
