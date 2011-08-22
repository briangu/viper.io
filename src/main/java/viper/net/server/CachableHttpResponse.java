package viper.net.server;


import java.io.IOException;
import java.nio.channels.FileChannel;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;


public class CachableHttpResponse extends DefaultHttpResponse
{
  private String _host;
  private String _requestUri;
  private int _cacheMaxAge;
  private FileChannel _fileChannel;

  public CachableHttpResponse(HttpVersion version, HttpResponseStatus status)
  {
    super(version, status);
  }

  public String getHost()
  {
    return _host;
  }

  public void setHost(String host)
  {
    _host = host;
  }

  public String getRequestUri()
  {
    return _requestUri;
  }

  public void setRequestUri(String requestUri)
  {
    this._requestUri = requestUri;
  }

  public int getCacheMaxAge()
  {
    return _cacheMaxAge;
  }

  public void setCacheMaxAge(int cacheMaxAge)
  {
    this._cacheMaxAge = cacheMaxAge;
  }

  public void setBackingFileChannel(FileChannel fileChannel)
  {
    this._fileChannel = fileChannel;
  }

  public FileChannel getFileChannel()
  {
    return _fileChannel;
  }

  public void dispose()
  {
    if (_fileChannel != null)
    {
      try
      {
        _fileChannel.close();
      }
      catch (IOException e)
      {
        e.printStackTrace();
      }
    }
  }
}
