package io.viper.core.server.router;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.util.CharsetUtil;
import org.jboss.netty.handler.codec.http.HttpResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class PostRoute extends SimpleChannelUpstreamHandler implements Route {

  String _rawRoute;
  List<String> _route;
  RouteHandler _handler;

  public PostRoute(String route, RouteHandler handler) {
    _rawRoute = route;
    _handler = handler;
    _route = RouteUtil.parsePath(route);
  }

  @Override
  public String getRoute() {
    return _rawRoute;
  }

  @Override
  public ChannelHandler getChannelHandler() {
    return this;
  }

  @Override
  public boolean isMatch(HttpRequest request) {
    if (!request.getMethod().equals(HttpMethod.POST)) return false;

    List<String> path = RouteUtil.parsePath(request.getUri());
    boolean isMatch = RouteUtil.match(_route, path);

    return isMatch;
  }

  @Override
  public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {

    HttpRequest request = (HttpRequest) ((MessageEvent) e).getMessage();

    if (request.getMethod() != HttpMethod.POST)
    {
      sendError(ctx, METHOD_NOT_ALLOWED);
      return;
    }

    // extract params

    List<String> path = RouteUtil.parsePath(request.getUri());

    Map<String, String> args = RouteUtil.extractPathArgs(_route, path);

    ChannelBuffer content = request.getContent();

    try
    {
      HttpResponse response = _handler.exec(args);

      if (response == null)
      {
        response = new DefaultHttpResponse(HTTP_1_1, OK);
      }

      if (response.getContent() != null && response.getContent().array().length > 0)
      {
        setContentLength(response, response.getContent().array().length);
      }

      ChannelFuture writeFuture = e.getChannel().write(response);
      if (!isKeepAlive(request))
      {
        writeFuture.addListener(ChannelFutureListener.CLOSE);
      }
    }
    catch (Exception ex)
    {
      ex.printStackTrace();
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    throws Exception
  {
    Channel ch = e.getChannel();
    Throwable cause = e.getCause();
    if (cause instanceof TooLongFrameException)
    {
      sendError(ctx, BAD_REQUEST);
      return;
    }

    cause.printStackTrace();
    if (ch.isConnected())
    {
      sendError(ctx, INTERNAL_SERVER_ERROR);
    }
  }

  private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status)
  {
    HttpResponse response = new DefaultHttpResponse(HTTP_1_1, status);
    response.setHeader(CONTENT_TYPE, "text/plain; charset=UTF-8");
    response.setContent(ChannelBuffers.copiedBuffer("Failure: " + status.toString() + "\r\n", CharsetUtil.UTF_8));

    // Close the connection as soon as the error message is sent.
    ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
  }
}
