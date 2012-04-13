package io.viper.common


import io.viper.core.server.file.{FileContentInfoProvider, InsertOnlyFileContentInfoCache, StaticFileContentInfoProvider}


object StaticFileContentInfoProviderFactory
{
  var _enableCache = true;

  def enableCache(flag: Boolean) = _enableCache = flag

  def create(clazz: Class[_], resourcePath: String): FileContentInfoProvider =
  {
    val rawFileProvider = StaticFileContentInfoProvider.create(this.getClass, resourcePath);
    if (_enableCache) new InsertOnlyFileContentInfoCache(rawFileProvider) else rawFileProvider
  }
}
