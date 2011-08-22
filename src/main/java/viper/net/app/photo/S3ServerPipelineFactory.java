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
package viper.net.app.photo;


import com.amazon.s3.QueryStringAuthGenerator;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Set;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import viper.net.server.chunkproxy.StaticFileServerHandler;
import viper.net.server.chunkproxy.s3.S3StaticFileServerHandler;
import viper.net.server.chunkproxy.HttpChunkProxyHandler;
import viper.net.server.chunkproxy.HttpChunkRelayProxy;
import viper.net.server.chunkproxy.s3.S3StandardChunkProxy;
import viper.net.server.router.Matcher;
import viper.net.server.router.RouterHandler;
import viper.net.server.router.UriStartsWithMatcher;

import static org.jboss.netty.channel.Channels.pipeline;


/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class S3ServerPipelineFactory implements ChannelPipelineFactory
{

  Set<ChannelHandlerContext> _listeners;

  private final ClientSocketChannelFactory _cf;
  private final String _remoteHost;
  private final int _remotePort;
  private final int _maxContentLength;
  QueryStringAuthGenerator _authGenerator;
  private final String _bucketName;

  public S3ServerPipelineFactory(QueryStringAuthGenerator authGenerator,
                                 String bucketName,
                                 ClientSocketChannelFactory cf,
                                 String remoteHost,
                                 int remotePort,
                                 int maxContentLength,
                                 Set<ChannelHandlerContext> listeners)
  {
    _authGenerator = authGenerator;
    _bucketName = bucketName;
    _listeners = listeners;
    _cf = cf;
    _remoteHost = remoteHost;
    _remotePort = remotePort;
    _maxContentLength = maxContentLength;
  }

  public ChannelPipeline getPipeline()
    throws Exception
  {
    HttpChunkRelayProxy proxy;

    String rootPath = "src/main/resources/public";
    new File(rootPath).mkdir();

    proxy =
      new S3StandardChunkProxy(
        _authGenerator,
        _bucketName,
        _cf,
        _remoteHost,
        _remotePort);

    FileUploadChunkRelayEventListener relayListener = new FileUploadChunkRelayEventListener();

    LinkedHashMap<Matcher, ChannelHandler> routes = new LinkedHashMap<Matcher, ChannelHandler>();
    routes.put(new UriStartsWithMatcher("/u/"), new HttpChunkProxyHandler(proxy, relayListener, _maxContentLength));
    routes.put(new UriStartsWithMatcher("/d/"), new S3StaticFileServerHandler(_authGenerator, _bucketName, _cf, _remoteHost, _remotePort));
    routes.put(new UriStartsWithMatcher("/"), new StaticFileServerHandler(rootPath));
//    pipeline.addLast("handler", new WebSocketServerHandler(_listeners));

    RouterHandler routerHandler = new RouterHandler("uri-handlers", routes);

    ChannelPipeline pipeline = pipeline();
    pipeline.addLast("decoder", new HttpRequestDecoder());
    pipeline.addLast("encoder", new HttpResponseEncoder());
    pipeline.addLast("router", routerHandler);

    return pipeline;
  }
}
