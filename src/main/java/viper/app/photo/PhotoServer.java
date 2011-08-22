/*
 * Copyright 2010 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package viper.app.photo;


import com.amazon.s3.QueryStringAuthGenerator;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;


/**
 * An HTTP server which serves Web Socket requests at:
 * <p/>
 * http://localhost:8080/websocket
 * <p/>
 * Open your browser at http://localhost:8080/, then the demo page will be loaded and a Web Socket connection will be
 * made automatically.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class PhotoServer
{
  private ServerBootstrap _bootstrap;

  public static PhotoServer create(int port,
                                   String awsId,
                                   String awsSecret,
                                   String bucketName)
  {
    Set<ChannelHandlerContext> listeners = new CopyOnWriteArraySet<ChannelHandlerContext>();

    PhotoServer photoServer = new PhotoServer();

    photoServer._bootstrap =
      new ServerBootstrap(
        new NioServerSocketChannelFactory(
          Executors.newCachedThreadPool(),
          Executors.newCachedThreadPool()));

    Executor executor = Executors.newCachedThreadPool();
    ClientSocketChannelFactory cf =
            new NioClientSocketChannelFactory(executor, executor);

    QueryStringAuthGenerator authGenerator = new QueryStringAuthGenerator(awsId, awsSecret, false);

    String remoteHost = String.format("%s.s3.amazonaws.com", bucketName);

    S3ServerPipelineFactory factory =
      new S3ServerPipelineFactory(
        authGenerator,
        bucketName,
        cf,
        remoteHost,
        80,
        (1024*1024)*1024,
        listeners);

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
