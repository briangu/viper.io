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
import java.util.Set;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import viper.net.server.StaticFileServerHandler;
import viper.net.server.chunkproxy.FileChunkProxy;
import viper.net.server.chunkproxy.FileUploadChunkRelayEventListener;
import viper.net.server.chunkproxy.HttpChunkProxyHandler;
import viper.net.server.chunkproxy.HttpChunkRelayProxy;
import viper.net.server.chunkproxy.s3.S3StandardChunkProxy;

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
    proxy = new FileChunkProxy(rootPath + "/uploads");

    proxy =
      new S3StandardChunkProxy(
        _authGenerator,
        _bucketName,
        _cf,
        _remoteHost,
        _remotePort);

    FileUploadChunkRelayEventListener relayListener = new FileUploadChunkRelayEventListener();

    ChannelPipeline pipeline = pipeline();
    pipeline.addLast("decoder", new HttpRequestDecoder());
    pipeline.addLast("aggregator", new HttpChunkProxyHandler(proxy, relayListener, _maxContentLength));
    pipeline.addLast("encoder", new HttpResponseEncoder());
//    pipeline.addLast("handler", new WebSocketServerHandler(_listeners));
    pipeline.addLast("static", new StaticFileServerHandler(rootPath));

    return pipeline;
  }
}
