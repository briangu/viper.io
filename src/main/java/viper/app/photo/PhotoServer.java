package viper.app.photo;


import com.amazon.s3.QueryStringAuthGenerator;
import java.io.File;
import java.net.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import viper.net.server.chunkproxy.HttpChunkProxyHandler;
import viper.net.server.chunkproxy.HttpChunkRelayProxy;
import viper.net.server.chunkproxy.StaticFileServerHandler;
import viper.net.server.chunkproxy.s3.S3StandardChunkProxy;
import viper.net.server.chunkproxy.s3.S3StaticFileServerHandler;
import viper.net.server.router.HostRouterHandler;
import viper.net.server.router.RouteMatcher;
import viper.net.server.router.RouterHandler;
import viper.net.server.router.UriRouteMatcher;


public class PhotoServer
{
  private ServerBootstrap _bootstrap;

  public static PhotoServer create(int port,
                                   String awsId,
                                   String awsSecret,
                                   String bucketName)
    throws URISyntaxException
  {
    Set<ChannelHandlerContext> listeners = new CopyOnWriteArraySet<ChannelHandlerContext>();

    PhotoServer photoServer = new PhotoServer();

    photoServer._bootstrap =
      new ServerBootstrap(
        new NioServerSocketChannelFactory(
          Executors.newCachedThreadPool(),
          Executors.newCachedThreadPool()));

    Executor executor = Executors.newCachedThreadPool();
    ClientSocketChannelFactory cf = new NioClientSocketChannelFactory(executor, executor);

    QueryStringAuthGenerator authGenerator = new QueryStringAuthGenerator(awsId, awsSecret, false);

    String remoteHost = String.format("%s.s3.amazonaws.com", bucketName);

    String staticFileRoot = "src/main/resources/public";

    new File(staticFileRoot).mkdir();

    PhotoServerChannelPipelineFactory photoServerChannelPipelineFactory =
      new PhotoServerChannelPipelineFactory(
        authGenerator,
        bucketName,
        cf,
        URI.create(String.format("http://%s:%s", remoteHost, 80)),
        (1024*1024)*1024,
        staticFileRoot);

    HostRouterHandler hostRouterHandler =
      createHostRouterHandler(
        URI.create(String.format("http://localhost:%s", port)),
        photoServerChannelPipelineFactory);

    ServerPipelineFactory factory = new ServerPipelineFactory(hostRouterHandler);

    photoServer._bootstrap.setPipelineFactory(factory);
    photoServer._bootstrap.bind(new InetSocketAddress(port));

    return photoServer;
  }

  static HostRouterHandler createHostRouterHandler(
    URI localHost,
    ChannelPipelineFactory lhPipelineFactory)
  {
    ConcurrentHashMap<String, ChannelPipelineFactory> routes = new ConcurrentHashMap<String, ChannelPipelineFactory>();

    List<String> localhostNames = new ArrayList<String>();
    localhostNames.add(localHost.getHost());

    try {
      String osHostName = InetAddress.getLocalHost().getHostName();
      localhostNames.add(osHostName);
      localhostNames.add(InetAddress.getLocalHost().getCanonicalHostName());

      if (osHostName.contains("."))
      {
        localhostNames.add(osHostName.substring(0, osHostName.indexOf(".")));
      }
    } catch (UnknownHostException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
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
      @Override
      public ChannelPipeline getPipeline() throws Exception
      {
        return new DefaultChannelPipeline() {{
          addLast("static", new StaticFileServerHandler("src/main/resources/public2"));
        }};
      }
    });

    routes.put(String.format("%s:%s", "amor", localHost.getPort()), new ChannelPipelineFactory()
    {
      @Override
      public ChannelPipeline getPipeline() throws Exception
      {
        return new DefaultChannelPipeline() {{
          addLast("static", new StaticFileServerHandler("src/main/resources/public3"));
        }};
      }
    });

    return new HostRouterHandler(routes);
  }

  private static class PhotoServerChannelPipelineFactory implements ChannelPipelineFactory
  {
    private final QueryStringAuthGenerator _authGenerator;
    private final ClientSocketChannelFactory _cf;
    private final URI _amazonHost;
    private final int _maxContentLength;
    private final String _bucketName;
    private final String _staticFileRoot;

    public PhotoServerChannelPipelineFactory(
      QueryStringAuthGenerator s3AuthGenerator,
      String s3BucketName,
      ClientSocketChannelFactory cf,
      URI amazonHost,
      int maxContentLength,
      String staticFileRoot)
    {
      _authGenerator = s3AuthGenerator;
      _bucketName = s3BucketName;
      _cf = cf;
      _amazonHost = amazonHost;
      _maxContentLength = maxContentLength;
      _staticFileRoot = staticFileRoot;
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception
    {
      HttpChunkRelayProxy proxy =
        new S3StandardChunkProxy(
          _authGenerator,
          _bucketName,
          _cf,
          _amazonHost);

      FileUploadChunkRelayEventListener relayListener = new FileUploadChunkRelayEventListener();

      LinkedHashMap<RouteMatcher, ChannelHandler> localhostRoutes = new LinkedHashMap<RouteMatcher, ChannelHandler>();
      localhostRoutes.put(new UriRouteMatcher(UriRouteMatcher.MatchMode.startsWith, "/u/"), new HttpChunkProxyHandler(proxy, relayListener, _maxContentLength));
      localhostRoutes.put(new UriRouteMatcher(UriRouteMatcher.MatchMode.startsWith, "/d/"), new S3StaticFileServerHandler(_authGenerator, _bucketName, _cf, _amazonHost));
      localhostRoutes.put(new UriRouteMatcher(UriRouteMatcher.MatchMode.startsWith, "/"), new StaticFileServerHandler(_staticFileRoot));
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
