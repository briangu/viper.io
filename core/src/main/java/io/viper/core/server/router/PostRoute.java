package io.viper.core.server.router;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.json.JSONObject;

import java.net.URI;
import java.net.URLDecoder;
import java.util.Arrays;
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

    byte[] body = content.array();
    String contentLengthHeader = request.getHeader(HttpHeaders.Names.CONTENT_LENGTH);

    int contentLength =
      contentLengthHeader != null
      ? Integer.parseInt(request.getHeader(HttpHeaders.Names.CONTENT_LENGTH))
      : body.length;

    if (contentLength != body.length) {
      body = Arrays.copyOfRange(body, 0, contentLength);
    }

    String rawContent = new String(body, "UTF-8");

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

    HttpResponse response;
    final RouteResponse[] routeResponse = new RouteResponse[1];

    try
    {
      routeResponse[0] = _handler.exec(args);
      response = routeResponse[0].HttpResponse;
    }
    catch (Exception ex)
    {
      ex.printStackTrace();
      routeResponse[0] = null;
      response = new StatusResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR).HttpResponse;
    }

    try
    {
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
        if (routeResponse[0] != null) routeResponse[0].dispose();
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
