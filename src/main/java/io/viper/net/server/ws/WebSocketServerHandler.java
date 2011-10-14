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
package io.viper.net.server.ws;


import static org.jboss.netty.handler.codec.http.HttpHeaders.*;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.*;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Values.*;
import static org.jboss.netty.handler.codec.http.HttpMethod.*;
import static org.jboss.netty.handler.codec.http.HttpVersion.*;

import com.amazon.thirdparty.Base64;
import java.security.MessageDigest;

import java.util.Set;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpHeaders.Values;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrame;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrameDecoder;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrameEncoder;
import org.jboss.netty.util.CharsetUtil;


/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2314 $, $Date: 2010-06-22 16:02:27 +0900 (Tue, 22 Jun 2010) $
 */
public class WebSocketServerHandler extends SimpleChannelUpstreamHandler
{

  private static final String WEBSOCKET_PATH = "/websocket";

  Set<ChannelHandlerContext> _listeners;

  public WebSocketServerHandler(Set<ChannelHandlerContext> listeners)
  {
    _listeners = listeners;
  }

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
    throws Exception
  {
    Object msg = e.getMessage();
    if (msg instanceof HttpRequest)
    {
      HttpRequest req = (HttpRequest)e.getMessage();

      if ((req.getMethod() == GET) && isWebsocketUpgradeRequest(req))
      {
        handleHttpRequest(ctx, (HttpRequest) msg);
        return;
      }
    }
    else if (msg instanceof WebSocketFrame)
    {
      handleWebSocketFrame(ctx, (WebSocketFrame) msg);
      return;
    }

    ctx.sendUpstream(e);
  }

  private boolean isWebsocketUpgradeRequest(HttpRequest req)
  {
    return (req.getUri().equals(WEBSOCKET_PATH) && Values.UPGRADE.equalsIgnoreCase(req.getHeader(CONNECTION)) && WEBSOCKET.equalsIgnoreCase(req.getHeader(Names.UPGRADE)));
  }

  private void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest req)
    throws Exception
  {
    // Serve the WebSocket handshake request.
    if (!isWebsocketUpgradeRequest(req)) return;

    // Create the WebSocket handshake response.
    HttpResponse res = new DefaultHttpResponse(HTTP_1_1,
                                               new HttpResponseStatus(101, "Web Socket Protocol Handshake"));
    res.addHeader(Names.UPGRADE, WEBSOCKET);
    res.addHeader(CONNECTION, Values.UPGRADE);

    // Fill in the headers and contents depending on handshake method.
    if (req.containsHeader(SEC_WEBSOCKET_KEY1) && req.containsHeader(SEC_WEBSOCKET_KEY2))
    {
      // New handshake method with a challenge:
      res.addHeader(SEC_WEBSOCKET_ORIGIN, req.getHeader(ORIGIN));
      res.addHeader(SEC_WEBSOCKET_LOCATION, getWebSocketLocation(req));
      String protocol = req.getHeader(SEC_WEBSOCKET_PROTOCOL);
      if (protocol != null)
      {
        res.addHeader(SEC_WEBSOCKET_PROTOCOL, protocol);
      }

      // Calculate the answer of the challenge.
      String key1 = req.getHeader(SEC_WEBSOCKET_KEY1);
      String key2 = req.getHeader(SEC_WEBSOCKET_KEY2);
      int a = (int) (Long.parseLong(key1.replaceAll("[^0-9]", "")) / key1.replaceAll("[^ ]", "").length());
      int b = (int) (Long.parseLong(key2.replaceAll("[^0-9]", "")) / key2.replaceAll("[^ ]", "").length());
      long c = req.getContent().readLong();
      ChannelBuffer input = ChannelBuffers.buffer(16);
      input.writeInt(a);
      input.writeInt(b);
      input.writeLong(c);
      ChannelBuffer output = ChannelBuffers.wrappedBuffer(MessageDigest.getInstance("MD5").digest(input.array()));
      res.setContent(output);
    }
    else if (req.containsHeader("Sec-WebSocket-Key"))
    {
      res.addHeader(SEC_WEBSOCKET_ORIGIN, req.getHeader(SEC_WEBSOCKET_ORIGIN));
      res.addHeader(SEC_WEBSOCKET_LOCATION, getWebSocketLocation(req));
      String protocol = req.getHeader(SEC_WEBSOCKET_PROTOCOL);
      if (protocol != null)
      {
        res.addHeader(SEC_WEBSOCKET_PROTOCOL, protocol);
      }
      String key1 = req.getHeader("Sec-WebSocket-Key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
      byte[] data = MessageDigest.getInstance("sha1").digest(key1.getBytes("UTF-8"));
      String accept = Base64.encodeBytes(data);
      res.addHeader("Sec-WebSocket-Accept", accept);
    }
    else
    {
      // Old handshake method with no challenge:
      res.addHeader(WEBSOCKET_ORIGIN, req.getHeader(ORIGIN));
      res.addHeader(WEBSOCKET_LOCATION, getWebSocketLocation(req));
      String protocol = req.getHeader(WEBSOCKET_PROTOCOL);
      if (protocol != null)
      {
        res.addHeader(WEBSOCKET_PROTOCOL, protocol);
      }
    }

    // Upgrade the connection and send the handshake response.
    ChannelPipeline p = ctx.getChannel().getPipeline();
    p.remove("aggregator");
    p.remove("router");
    p.replace("decoder", "wsdecoder", new WebSocketFrameDecoder());

    ctx.getChannel().write(res);

    if (!_listeners.contains(ctx))
    {
      _listeners.add(ctx);
    }

    p.replace("encoder", "wsencoder", new WebSocketFrameEncoder());
  }

  private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame)
  {
/*
        // Send the uppercased string back.
        ctx.getChannel().write(
                new DefaultWebSocketFrame(frame.getTextData().toUpperCase()));
*/
    if (!_listeners.contains(ctx))
    {
      _listeners.add(ctx);
    }
  }

  private void sendHttpResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse res)
  {
    // Generate an error page if response status code is not OK (200).
    if (res.getStatus().getCode() != 200)
    {
      res.setContent(ChannelBuffers.copiedBuffer(res.getStatus().toString(), CharsetUtil.UTF_8));
      setContentLength(res, res.getContent().readableBytes());
    }

    // Send the response and close the connection if necessary.
    ChannelFuture f = ctx.getChannel().write(res);
    if (!isKeepAlive(req) || res.getStatus().getCode() != 200)
    {
      f.addListener(ChannelFutureListener.CLOSE);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    throws Exception
  {
    e.getCause().printStackTrace();
    e.getChannel().close();
  }

  private String getWebSocketLocation(HttpRequest req)
  {
    return "ws://" + req.getHeader(HttpHeaders.Names.HOST) + WEBSOCKET_PATH;
  }
}
