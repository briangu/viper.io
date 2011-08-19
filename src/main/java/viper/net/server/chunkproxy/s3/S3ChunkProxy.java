package viper.net.server.chunkproxy.s3;


import com.amazon.s3.QueryStringAuthGenerator;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpChunk;
import viper.net.server.chunkproxy.HttpChunkProxyEventListener;
import viper.net.server.chunkproxy.HttpChunkRelayProxy;


public class S3ChunkProxy implements HttpChunkRelayProxy
{

  final HttpChunkRelayProxy _multipartProxy;
  final HttpChunkRelayProxy _standardProxy;
  HttpChunkRelayProxy _currentProxy;

  final long _multipartThresholdSize;

  static final long DEFAULT_MAXMULTIPART_THRESHOLD_SIZE = 1024 * 1024;

  public S3ChunkProxy(QueryStringAuthGenerator s3AuthGenerator,
                      String bucketName,
                      ClientSocketChannelFactory cf,
                      String remoteHost,
                      int remotePort)
  {
    this(
      s3AuthGenerator,
      bucketName,
      cf,
      remoteHost,
      remotePort,
      DEFAULT_MAXMULTIPART_THRESHOLD_SIZE);
  }

  public S3ChunkProxy(QueryStringAuthGenerator s3AuthGenerator,
                      String bucketName,
                      ClientSocketChannelFactory cf,
                      String remoteHost,
                      int remotePort,
                      long multipartThresholdSize)
  {
    _multipartThresholdSize = multipartThresholdSize;

    _multipartProxy = new S3MultipartChunkProxy(s3AuthGenerator, bucketName, cf, remoteHost, remotePort);
    _standardProxy = new S3StandardChunkProxy(s3AuthGenerator, bucketName, cf, remoteHost, remotePort);
  }

  @Override
  public boolean isRelaying()
  {
    return _currentProxy != null ? _currentProxy.isRelaying() : false;
  }

  @Override
  public void init(HttpChunkProxyEventListener listener, String objectName, long objectSize, String contentType)
    throws Exception
  {
    _currentProxy = (objectSize > _multipartThresholdSize)
                ? _multipartProxy
                : _standardProxy;
    _currentProxy.init(listener, objectName, objectSize, contentType);
  }

  @Override
  public void appendChunk(HttpChunk chunk)
  {
    _currentProxy.appendChunk(chunk);
  }

  @Override
  public void complete(HttpChunk chunk)
  {
    _currentProxy.complete(chunk);
    _currentProxy = null;
  }

  @Override
  public void abort()
  {
    _currentProxy.abort();
  }
}

