package io.viper.core.server.router;

import java.net.URI;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;

import java.util.List;
import java.util.Map;

import static org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer;
import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class GetRoute extends RestRoute
{
  public GetRoute(String route, RouteHandler handler)
  {
    super(route, handler, HttpMethod.GET);
  }

  @Override
  public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {

    if (!(e instanceof MessageEvent) || !(((MessageEvent) e).getMessage() instanceof HttpRequest))
    {
      super.handleUpstream(ctx, e);
      return;
    }

    HttpRequest request = (HttpRequest) ((MessageEvent) e).getMessage();

    if (request.getMethod() != HttpMethod.GET)
    {
      sendError(ctx, METHOD_NOT_ALLOWED);
      return;
    }

    List<String> path = RouteUtil.parsePath(request.getUri());

    Map<String, String> args = RouteUtil.extractPathArgs(_route, path);
    args.putAll(RouteUtil.extractQueryParams(new URI(request.getUri())));
    
    try
    {
      final RouteResponse routeResponse = _handler.exec(args);

      HttpResponse response = routeResponse.HttpResponse;

      if (response == null)
      {
        response = new DefaultHttpResponse(HTTP_1_1, OK);
        response.setContent(wrappedBuffer("{\"status\": true}".getBytes()));
      }
      else
      {
        if (response.getContent().hasArray())
        {
          if (response.getContent().array().length > 0)
          {
            setContentLength(response, response.getContent().array().length);
          }
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

      if (response.getStatus() != HttpResponseStatus.OK || !isKeepAlive(request))
      {
        writeFuture.addListener(ChannelFutureListener.CLOSE);
      }
      writeFuture.addListener(ChannelFutureListener.CLOSE);
    }
    catch (Exception ex)
    {
      ex.printStackTrace();
    }
  }
}
