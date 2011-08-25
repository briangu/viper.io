package viper.app.photo;


import com.amazon.s3.QueryStringAuthGenerator;
import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.DefaultChannelPipeline;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import viper.net.server.CacheHandler;
import viper.net.server.chunkproxy.StaticFileServerHandler;
import viper.net.server.chunkproxy.s3.S3StaticFileServerHandler;
import viper.net.server.chunkproxy.HttpChunkProxyHandler;
import viper.net.server.chunkproxy.HttpChunkRelayProxy;
import viper.net.server.chunkproxy.s3.S3StandardChunkProxy;
import viper.net.server.router.HostRouterHandler;
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
                               QueryStringAuthGenerator s3AuthGenerator,
                               String s3BucketName,
                               ClientSocketChannelFactory cf,
                               URI amazonHost,
                               int maxContentLength,
                               Set<ChannelHandlerContext> listeners,
                               String staticFileRoot)
  {
    _localHost = localHost;
    _authGenerator = s3AuthGenerator;
    _bucketName = s3BucketName;
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

    proxy =
      new S3StandardChunkProxy(
        _authGenerator,
        _bucketName,
        _cf,
        _amazonHost);

    FileUploadChunkRelayEventListener relayListener = new FileUploadChunkRelayEventListener();

    LinkedHashMap<RouteMatcher, ChannelHandler> localhostRoutes = new LinkedHashMap<RouteMatcher, ChannelHandler>();
    localhostRoutes.put(new UriStartsWithRouteMatcher("/u/"), new HttpChunkProxyHandler(proxy, relayListener, _maxContentLength));
    localhostRoutes.put(new UriStartsWithRouteMatcher("/d/"), new S3StaticFileServerHandler(_authGenerator, _bucketName, _cf, _amazonHost));
    localhostRoutes.put(new UriStartsWithRouteMatcher("/"), new StaticFileServerHandler(_staticFileRoot, 60*60));
//    pipeline.addLast("handler", new WebSocketServerHandler(_listeners));

    ChannelPipeline lhPipeline = new DefaultChannelPipeline();
    lhPipeline.addLast("cache", new CacheHandler());
    lhPipeline.addLast("router", new RouterHandler("uri-handlers", localhostRoutes));

    ConcurrentHashMap<String, ChannelPipeline> routes = new ConcurrentHashMap<String, ChannelPipeline>();

    List<String> localhostNames = new ArrayList<String>();
    localhostNames.add(_localHost.getHost());
    localhostNames.add(InetAddress.getLocalHost().getHostName());
    localhostNames.add(InetAddress.getLocalHost().getHostName().substring(0, InetAddress.getLocalHost().getHostName().indexOf(".")));
    localhostNames.add(InetAddress.getLocalHost().getCanonicalHostName());

    for (String hostname : localhostNames)
    {
      routes.put(String.format("%s:%s", hostname, _localHost.getPort()), lhPipeline);
    }

    HostRouterHandler hostRouterHandler = new HostRouterHandler(routes);

    ChannelPipeline pipeline = pipeline();
    pipeline.addLast("decoder", new HttpRequestDecoder());
    pipeline.addLast("encoder", new HttpResponseEncoder());
    pipeline.addLast("hostrouter", hostRouterHandler);

    return pipeline;
  }
}
