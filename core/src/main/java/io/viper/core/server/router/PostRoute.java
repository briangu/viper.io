package io.viper.core.server.router;

import java.net.URI;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer;
import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class PostRoute extends RestRoute {

  public PostRoute(String route, RouteHandler handler) {
    super(route, handler, HttpMethod.POST);
  }

  @Override
  public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {

    if (!(e instanceof MessageEvent) || !(((MessageEvent) e).getMessage() instanceof HttpRequest))
    {
      super.handleUpstream(ctx, e);
      return;
    }

    HttpRequest request = (HttpRequest) ((MessageEvent) e).getMessage();

    if (request.getMethod() != HttpMethod.POST)
    {
      sendError(ctx, METHOD_NOT_ALLOWED);
      return;
    }

    // extract params

    List<String> path = RouteUtil.parsePath(request.getUri());

    Map<String, String> args = RouteUtil.extractPathArgs(_route, path);
    args.putAll(RouteUtil.extractQueryParams(new URI(request.getUri())));

    ChannelBuffer content = request.getContent();

    if (!content.hasArray())
    {
      sendError(ctx, BAD_REQUEST);
      return;
    }

    String rawContent = new String(content.array(), "UTF-8");

    if (rawContent.startsWith("{"))
    {
      JSONObject json = new JSONObject(rawContent);

      Iterator keys = json.keys();
      while(keys.hasNext())
      {
        String key = keys.next().toString();
        args.put(key, json.getString(key));
      }
    }
    else
    {
      args.putAll(RouteUtil.extractQueryParams(rawContent));
    }

    try
    {
      final RouteResponse routeResponse = _handler.exec(args);
      HttpResponse response = routeResponse.HttpResponse;

      if (response == null)
      {
        response = new DefaultHttpResponse(HTTP_1_1, OK);
        response.setContent(wrappedBuffer("{\"status\": true}".getBytes()));
      }

      if (response.getContent() != null && response.getContent().array().length > 0)
      {
        setContentLength(response, response.getContent().array().length);
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
    }
    catch (Exception ex)
    {
      ex.printStackTrace();
    }
  }
}
