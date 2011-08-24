package viper.app.photo;


import com.amazon.s3.QueryStringAuthGenerator;
import java.io.File;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import viper.net.server.CacheHandler;
import viper.net.server.chunkproxy.StaticFileServerHandler;
import viper.net.server.chunkproxy.s3.S3StaticFileServerHandler;
import viper.net.server.chunkproxy.HttpChunkProxyHandler;
import viper.net.server.chunkproxy.HttpChunkRelayProxy;
import viper.net.server.chunkproxy.s3.S3StandardChunkProxy;
import viper.net.server.router.RouteMatcher;
import viper.net.server.router.RouterHandler;
import viper.net.server.router.UriStartsWithRouteMatcher;

import static org.jboss.netty.channel.Channels.pipeline;

public class ServerPipelineFactory implements ChannelPipelineFactory
{
  Set<ChannelHandlerContext> _listeners;

  private final URI _localHost;
  private final QueryStringAuthGenerator _authGenerator;
  private final ClientSocketChannelFactory _cf;
  private final URI _amazonHost;
  private final int _maxContentLength;
  private final String _bucketName;
  private final String _staticFileRoot;

  public ServerPipelineFactory(URI localHost,
                               QueryStringAuthGenerator authGenerator,
                               String bucketName,
                               ClientSocketChannelFactory cf,
                               URI amazonHost,
                               int maxContentLength,
                               Set<ChannelHandlerContext> listeners,
                               String staticFileRoot)
  {
    _localHost = localHost;
    _authGenerator = authGenerator;
    _bucketName = bucketName;
    _listeners = listeners;
    _cf = cf;
    _amazonHost = amazonHost;
    _maxContentLength = maxContentLength;
    _staticFileRoot = staticFileRoot;
  }

  public ChannelPipeline getPipeline()
    throws Exception
  {
    HttpChunkRelayProxy proxy;

    new File(_staticFileRoot).mkdir();

    proxy =
      new S3StandardChunkProxy(
        _authGenerator,
        _bucketName,
        _cf,
        _amazonHost);

    FileUploadChunkRelayEventListener relayListener = new FileUploadChunkRelayEventListener();

    ConcurrentHashMap<String, LinkedHashMap<RouteMatcher, ChannelHandler>> routes =
      new ConcurrentHashMap<String, LinkedHashMap<RouteMatcher, ChannelHandler>>();

    LinkedHashMap<RouteMatcher, ChannelHandler> localhostRoutes = new LinkedHashMap<RouteMatcher, ChannelHandler>();
    localhostRoutes.put(new UriStartsWithRouteMatcher("/u/"), new HttpChunkProxyHandler(proxy, relayListener, _maxContentLength));
    localhostRoutes.put(new UriStartsWithRouteMatcher("/d/"), new S3StaticFileServerHandler(_authGenerator, _bucketName, _cf, _amazonHost));
    localhostRoutes.put(new UriStartsWithRouteMatcher("/"), new StaticFileServerHandler(_staticFileRoot, 60*60));
//    pipeline.addLast("handler", new WebSocketServerHandler(_listeners));
    routes.put(String.format("%s:%s", _localHost.getHost(), _localHost.getPort()), localhostRoutes);

    RouterHandler routerHandler = new RouterHandler("uri-handlers", routes);

    ChannelPipeline pipeline = pipeline();
    pipeline.addLast("decoder", new HttpRequestDecoder());
    pipeline.addLast("encoder", new HttpResponseEncoder());
    pipeline.addLast("cache", new CacheHandler());
    pipeline.addLast("router", routerHandler);

    return pipeline;
  }
}
