package io.viper.net.server.chunkproxy;


public interface FileContentInfoProvider
{
  public FileContentInfo getFileContent(String rootPath);
}
