package viper.app.photo;


import com.amazon.s3.QueryStringAuthGenerator;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.omg.CosNaming.NamingContextExtPackage.StringNameHelper;


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

    ServerPipelineFactory factory =
      new ServerPipelineFactory(
        URI.create(String.format("http://localhost:%s", port)),
        authGenerator,
        bucketName,
        cf,
        URI.create(String.format("http://%s:%s", remoteHost, 80)),
        (1024*1024)*1024,
        listeners,
        staticFileRoot);

    // Set up the event pipeline factory.
    photoServer._bootstrap.setPipelineFactory(factory);

    // Bind and start to accept incoming connections.
    photoServer._bootstrap.bind(new InetSocketAddress(port));

//    new Thread(new CounterRunnable(listeners)).start();

    return photoServer;
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
