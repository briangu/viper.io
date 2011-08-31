package io.viper.app.photo;


import com.amazon.s3.QueryStringAuthGenerator;
import io.viper.net.server.chunkproxy.MappedFileServerHandler;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.DefaultChannelPipeline;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import io.viper.net.server.chunkproxy.FileChunkProxy;
import io.viper.net.server.chunkproxy.FileContentInfo;
import io.viper.net.server.chunkproxy.FileContentInfoProvider;
import io.viper.net.server.chunkproxy.HttpChunkProxyHandler;
import io.viper.net.server.chunkproxy.HttpChunkRelayProxy;
import io.viper.net.server.chunkproxy.InsertOnlyFileContentInfoCache;
import io.viper.net.server.chunkproxy.StaticFileContentInfoProvider;
import io.viper.net.server.chunkproxy.StaticFileServerHandler;
import io.viper.net.server.chunkproxy.s3.S3StandardChunkProxy;
import io.viper.net.server.chunkproxy.s3.S3StaticFileServerHandler;
import io.viper.net.server.router.HostRouterHandler;
import io.viper.net.server.router.RouteMatcher;
import io.viper.net.server.router.RouterHandler;
import io.viper.net.server.router.UriRouteMatcher;
import org.json.JSONException;


public class PhotoServer
{
  private ServerBootstrap _bootstrap;

  public static PhotoServer create(int port, String staticFileRoot)
    throws URISyntaxException, IOException, JSONException
  {
    PhotoServer photoServer = new PhotoServer();

    photoServer._bootstrap =
        new ServerBootstrap(
            new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool()));

    String localhost = String.format("http://%s:%s", InetAddress.getLocalHost().getHostName(), port);

    ChannelPipelineFactory photoServerChannelPipelineFactory =
        new LocalPhotoServerChannelPipelineFactory(
            (1024 * 1024) * 1024,
            "/tmp/uploads",
            staticFileRoot,
            localhost + "/d/");

    HostRouterHandler hostRouterHandler =
        createHostRouterHandler(
            URI.create(localhost),
            photoServerChannelPipelineFactory);

    ServerPipelineFactory factory = new ServerPipelineFactory(hostRouterHandler);

    photoServer._bootstrap.setPipelineFactory(factory);
    photoServer._bootstrap.bind(new InetSocketAddress(port));

    return photoServer;
  }

  public static PhotoServer createWithS3(int port, String awsId, String awsSecret, String bucketName)
    throws URISyntaxException, IOException, JSONException
  {
    PhotoServer photoServer = new PhotoServer();

    photoServer._bootstrap = new ServerBootstrap(
        new NioServerSocketChannelFactory(
          Executors.newCachedThreadPool(),
          Executors.newCachedThreadPool()));

    Executor executor = Executors.newCachedThreadPool();
    ClientSocketChannelFactory cf = new NioClientSocketChannelFactory(executor, executor);

    QueryStringAuthGenerator authGenerator = new QueryStringAuthGenerator(awsId, awsSecret, false);

    String remoteHost = String.format("%s.s3.amazonaws.com", bucketName);
    String localhost = String.format("http://%s:%s", InetAddress.getLocalHost().getHostName(), port);

    String staticFileRoot = "/Users/bguarrac/scm/viper/src/main/resources/public";

    ChannelPipelineFactory photoServerChannelPipelineFactory =
        new AmazonPhotoServerChannelPipelineFactory(
            authGenerator,
            bucketName,
            cf,
            URI.create(String.format("http://%s:%s", remoteHost,80)),
            (1024 * 1024) * 1024,
            staticFileRoot,
            localhost + "/d/");

    HostRouterHandler hostRouterHandler =
        createHostRouterHandler(
            URI.create(localhost),
            photoServerChannelPipelineFactory);

    ServerPipelineFactory factory = new ServerPipelineFactory(hostRouterHandler);

    photoServer._bootstrap.setPipelineFactory(factory);
    photoServer._bootstrap.bind(new InetSocketAddress(port));

    return photoServer;
  }

  static HostRouterHandler createHostRouterHandler(URI localHost, ChannelPipelineFactory lhPipelineFactory)
    throws IOException, JSONException
  {
    ConcurrentHashMap<String, ChannelPipelineFactory> routes = new ConcurrentHashMap<String, ChannelPipelineFactory>();

    List<String> localhostNames = new ArrayList<String>();
    localhostNames.add(localHost.getHost());

    try
    {
      String osHostName = InetAddress.getLocalHost().getHostName();
      localhostNames.add(osHostName);
      localhostNames.add(InetAddress.getLocalHost().getCanonicalHostName());

      if (osHostName.contains("."))
      {
        localhostNames.add(osHostName.substring(0, osHostName.indexOf(".")));
      }
    }
    catch (UnknownHostException e)
    {
      e.printStackTrace();
    }

    for (String hostname : localhostNames)
    {
      if (localHost.getPort() == 80)
      {
        routes.put(hostname, lhPipelineFactory);
      }
      routes.put(String.format("%s:%s", hostname, localHost.getPort()), lhPipelineFactory);
    }

    routes.put(String.format("%s:%s", "nebulous", localHost.getPort()), new ChannelPipelineFactory()
    {
      String _rootPath = "src/main/resources/public2";
      MappedFileServerHandler _fileServerHandler = MappedFileServerHandler.create(_rootPath);

      @Override
      public ChannelPipeline getPipeline()
          throws Exception
      {
        return new DefaultChannelPipeline()
        {{
            addLast("static", _fileServerHandler);
        }};
      }
    });

    routes.put(String.format("%s:%s", "amor", localHost.getPort()), new ChannelPipelineFactory()
    {
      String _rootPath = "src/main/resources/public3";
      MappedFileServerHandler _fileServerHandler = MappedFileServerHandler.create(_rootPath);

      @Override
      public ChannelPipeline getPipeline()
          throws Exception
      {
        return new DefaultChannelPipeline()
        {{
            addLast("static", _fileServerHandler);
        }};
      }
    });

    return new HostRouterHandler(routes);
  }

  private static class LocalPhotoServerChannelPipelineFactory implements ChannelPipelineFactory
  {
    private final int _maxContentLength;
    private final String _uploadFileRoot;
    private final String _staticFileRoot;
    private final MappedFileServerHandler _staticFileProvider;
    private final FileContentInfoProvider _uploadFileProvider;
    private final String _downloadHostname;

    public LocalPhotoServerChannelPipelineFactory(
      int maxContentLength,
      String uploadFileRoot,
      String staticFileRoot,
      String downloadHostname)
        throws IOException, JSONException
    {
      _maxContentLength = maxContentLength;
      _uploadFileRoot = uploadFileRoot;
      _staticFileRoot = staticFileRoot;
      _downloadHostname = downloadHostname;

      _staticFileProvider = MappedFileServerHandler.create(_staticFileRoot);
      _uploadFileProvider = new StaticFileContentInfoProvider(_uploadFileRoot);
    }

    @Override
    public ChannelPipeline getPipeline()
        throws Exception
    {
      HttpChunkRelayProxy proxy = new FileChunkProxy(_uploadFileRoot);

      FileUploadChunkRelayEventListener relayListener =
        new FileUploadChunkRelayEventListener(_downloadHostname);

      LinkedHashMap<RouteMatcher, ChannelHandler> localhostRoutes = new LinkedHashMap<RouteMatcher, ChannelHandler>();
      localhostRoutes.put(new UriRouteMatcher(UriRouteMatcher.MatchMode.startsWith, "/u/"),
                          new HttpChunkProxyHandler(proxy, relayListener, _maxContentLength));
      localhostRoutes.put(new UriRouteMatcher(UriRouteMatcher.MatchMode.startsWith, "/d/"),
                          new StaticFileServerHandler(_uploadFileProvider));
      localhostRoutes.put(new UriRouteMatcher(UriRouteMatcher.MatchMode.startsWith, "/"),
                          _staticFileProvider);
//    pipeline.addLast("handler", new WebSocketServerHandler(_listeners));

      ChannelPipeline lhPipeline = new DefaultChannelPipeline();
      lhPipeline.addLast("router", new RouterHandler("uri-handlers", localhostRoutes));

      return lhPipeline;
    }
  }

  private static class AmazonPhotoServerChannelPipelineFactory implements ChannelPipelineFactory
  {
    private final QueryStringAuthGenerator _authGenerator;
    private final ClientSocketChannelFactory _cf;
    private final URI _amazonHost;
    private final int _maxContentLength;
    private final String _bucketName;
    private final String _staticFileRoot;
    private final FileContentInfoProvider _staticFileProvider;
    private final FileContentInfoProvider _staticFileCache;
    private final String _downloadHostname;

    public AmazonPhotoServerChannelPipelineFactory(
      QueryStringAuthGenerator s3AuthGenerator,
      String s3BucketName,
      ClientSocketChannelFactory cf,
      URI amazonHost,
      int maxContentLength,
      String staticFileRoot,
      String downloadHostname)
    {
      _authGenerator = s3AuthGenerator;
      _bucketName = s3BucketName;
      _cf = cf;
      _amazonHost = amazonHost;
      _maxContentLength = maxContentLength;
      _staticFileRoot = staticFileRoot;
      _downloadHostname = downloadHostname;

      _staticFileProvider = new StaticFileContentInfoProvider(_staticFileRoot);
      _staticFileCache = new InsertOnlyFileContentInfoCache(_staticFileProvider);
    }

    @Override
    public ChannelPipeline getPipeline()
        throws Exception
    {
      HttpChunkRelayProxy proxy;

      proxy = new S3StandardChunkProxy(_authGenerator, _bucketName, _cf, _amazonHost);

      String uploadFileRoot = _staticFileRoot + "/uploads";
      proxy = new FileChunkProxy(uploadFileRoot);

      FileUploadChunkRelayEventListener relayListener =
        new FileUploadChunkRelayEventListener(_downloadHostname);

      LinkedHashMap<RouteMatcher, ChannelHandler> localhostRoutes = new LinkedHashMap<RouteMatcher, ChannelHandler>();
      localhostRoutes.put(new UriRouteMatcher(UriRouteMatcher.MatchMode.startsWith, "/u/"),
                          new HttpChunkProxyHandler(proxy, relayListener, _maxContentLength));
      localhostRoutes.put(new UriRouteMatcher(UriRouteMatcher.MatchMode.startsWith, "/d/"),
                          new S3StaticFileServerHandler(_authGenerator, _bucketName, _cf, _amazonHost));
      localhostRoutes.put(new UriRouteMatcher(UriRouteMatcher.MatchMode.startsWith, "/"),
                          new StaticFileServerHandler(_staticFileCache));
//    pipeline.addLast("handler", new WebSocketServerHandler(_listeners));

      ChannelPipeline lhPipeline = new DefaultChannelPipeline();
      lhPipeline.addLast("router", new RouterHandler("uri-handlers", localhostRoutes));

      return lhPipeline;
    }
  }

/*
  public static class CounterRunnable implements Runnable
  {
    Set<ChannelHandlerContext> _listeners;

    public CounterRunnable(Set<ChannelHandlerContext> listeners)
    {
      _listeners = listeners;
    }

    @Override
    public void run()
    {
      int i = 0;

      while (true)
      {
        SimpleConsumer consumer = new SimpleConsumer("127.0.0.1", 9092, 10000, 1024000);

        long offset = 0;

        while (true)
        {
          // create a fetch request for topic “test”, partition 0, current offset, and fetch size of 1MB
          FetchRequest fetchRequest = new FetchRequest("test", 0, offset, 1000000);

          // get the message set from the consumer and print them out
          ByteBufferMessageSet messages = consumer.fetch(fetchRequest);
          for (Message message : messages)
          {
//            String data = Utils.toString(message.payload(), "UTF-8").toString();
            ByteBuffer buf = message.payload();
            Charset cs = Charset.forName("UTF-8");
            CharBuffer chbuf = cs.decode(buf);

            String data = chbuf.toString();

//            System.out.println("consumed: " + data);

            for (ChannelHandlerContext ctx : _listeners)
            {
              ctx.getChannel().write(new DefaultWebSocketFrame(data));
            }

            // advance the offset after consuming each message
            offset += MessageSet.entrySize(message);

            try
            {
              Thread.sleep(2000);
            }
            catch (Exception e)
            {

            }
          }
        }
      }
    }
  }
*/
}
