package io.viper.net.server.chunkproxy;


public interface FileContentInfoProvider
{
  FileContentInfo getFileContent(String rootPath);
  void dispose(FileContentInfo info);
}
