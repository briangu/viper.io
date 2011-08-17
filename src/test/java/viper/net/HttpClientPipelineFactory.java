package viper.net;

import static org.jboss.netty.channel.Channels.*;

import com.amazonaws.http.HttpResponseHandler;
import java.util.Set;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.logging.LoggingHandler;
import org.jboss.netty.logging.InternalLogLevel;
import viper.net.server.HttpChunkRelayHandler;


/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev$, $Date$
 */
public class HttpClientPipelineFactory implements ChannelPipelineFactory {

    private final ClientSocketChannelFactory cf;
    private final String remoteHost;
    private final int remotePort;
    private final int maxContentLength;
    Set<ChannelHandlerContext> listeners;

    public HttpClientPipelineFactory(ClientSocketChannelFactory cf, String remoteHost, int remotePort, int maxContentLength, Set<ChannelHandlerContext> listeners)
    {
      this.cf = cf;
      this.remoteHost = remoteHost;
      this.remotePort = remotePort;
      this.maxContentLength = maxContentLength;
      this.listeners = listeners;
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = pipeline();

        pipeline.addLast("log", new LoggingHandler(InternalLogLevel.INFO));
        pipeline.addLast("decoder", new HttpRequestDecoder());
        pipeline.addLast("aggregator", new StreamChunkAggregator(cf, remoteHost, remotePort, maxContentLength));
        pipeline.addLast("encoder", new HttpResponseEncoder());
        pipeline.addLast("handler", new HttpChunkRelayHandler(listeners));

        return pipeline;
    }
}
