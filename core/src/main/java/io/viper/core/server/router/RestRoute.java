package io.viper.core.server.router;


import io.viper.core.server.security.AuthHandler;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer;
import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;


public abstract class RestRoute extends Route
{
  protected RouteHandler _handler;
  protected HttpMethod _method;

  protected RestRoute(String route, RouteHandler handler, HttpMethod method)
  {
    this(route, method, handler, null);
  }

  protected RestRoute(String route, HttpMethod method, RouteHandler handler, AuthHandler authHandler)
  {
    super(route, authHandler);
    _handler = handler;
    _method = method;
  }

  public boolean isMatch(HttpRequest request)
  {
    return (super.isMatch(request) && request.getMethod().equals(_method));
  }

  @Override
  public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {

    if (!(e instanceof MessageEvent) || !(((MessageEvent) e).getMessage() instanceof HttpRequest))
    {
      super.handleUpstream(ctx, e);
      return;
    }

    HttpRequest request = (HttpRequest) ((MessageEvent) e).getMessage();

    List<String> path = RouteUtil.parsePath(request.getUri());

    Map<String, String> args = RouteUtil.extractPathArgs(_route, path);
    args.putAll(RouteUtil.extractQueryParams(new URI(request.getUri())));

    try
    {
      final RouteResponse routeResponse = _handler.exec(args);

      final Boolean keepalive = isKeepAlive(request);

      HttpResponse response = routeResponse.HttpResponse;

      if (response == null)
      {
        response = new DefaultHttpResponse(HTTP_1_1, OK);
        response.setContent(wrappedBuffer("{\"status\": true}".getBytes()));
      }
      else
      {
        if (keepalive)
        {
          response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
          setContentLength(response, response.getContent().readableBytes());
        }
        else
        {
          response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
        }
      }

      ChannelFuture writeFuture = e.getChannel().write(response);
      writeFuture.addListener(new ChannelFutureListener()
      {
        @Override
        public void operationComplete(ChannelFuture channelFuture)
          throws Exception
        {
          routeResponse.dispose();
        }
      });

      if (!keepalive)
      {
        writeFuture.addListener(ChannelFutureListener.CLOSE);
      }
    }
    catch (Exception ex)
    {
      ex.printStackTrace();
    }
  }
}
