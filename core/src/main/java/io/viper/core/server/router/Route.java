package io.viper.core.server.router;


import io.viper.core.server.security.AuthHandler;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.util.CharsetUtil;

import java.util.List;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;


public abstract class Route extends SimpleChannelUpstreamHandler
{
  protected String _rawRoute;
  protected List<String> _route;
  protected AuthHandler _authHandler;

  protected Route(String route)
  {
    this(route, null);
  }

  protected Route(String route, AuthHandler authHandler)
  {
    _rawRoute = route;
    _route = RouteUtil.parsePath(route);
    _authHandler = authHandler;
  }

  public String getRoute()
  {
    return _rawRoute;
  }

  public ChannelHandler getChannelHandler()
  {
    return this;
  }

  public boolean isMatch(HttpRequest request)
  {
    List<String> path = RouteUtil.parsePath(request.getUri());
    boolean isMatch = RouteUtil.match(_route, path);
    return isMatch;
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

  protected void sendError(ChannelHandlerContext ctx, HttpResponseStatus status)
  {
    HttpResponse response = new DefaultHttpResponse(HTTP_1_1, status);
    response.setHeader(CONTENT_TYPE, "text/plain; charset=UTF-8");
    response.setContent(ChannelBuffers.copiedBuffer("Failure: " + status.toString() + "\r\n", CharsetUtil.UTF_8));

    // Close the connection as soon as the error message is sent.
    ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
  }

  public boolean isAuthorized(HttpRequest request)
  {
    return _authHandler == null || _authHandler.isAuthorized(request);
  }
}
