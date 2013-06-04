package io.viper.common


import io.viper.core.server.file.{FileContentInfoProvider, InsertOnlyFileContentInfoCache, StaticFileContentInfoProvider}


object StaticFileContentInfoProviderFactory
{
  var _enableCache = true

  def enableCache(enabled: Boolean) {
    _enableCache = enabled
  }

  def create(clazz: Class[_], resourcePath: String): FileContentInfoProvider = {
    val rawFileProvider = StaticFileContentInfoProvider.create(clazz, resourcePath)
    if (_enableCache) new InsertOnlyFileContentInfoCache(rawFileProvider) else rawFileProvider
  }
}
