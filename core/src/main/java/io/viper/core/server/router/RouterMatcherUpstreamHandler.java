package io.viper.core.server.router;


import io.viper.core.server.StatusResponseHandler;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.util.CharsetUtil;

import java.util.ArrayList;
import java.util.List;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;


public class RouterMatcherUpstreamHandler extends SimpleChannelUpstreamHandler
{

  private static final ChannelHandler HANDLER_401 = new StatusResponseHandler("Not authorized", 401);
  private static final ChannelHandler HANDLER_404 = new StatusResponseHandler("Not found", 404);

  private final String _handlerName;

  List<Route> _routes = new ArrayList<Route>();

  public RouterMatcherUpstreamHandler(
    String handlerName,
    List<Route> routes)
  {
    _handlerName = handlerName;
    _routes = routes;
  }

  @Override
  public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
      throws Exception
  {
    if (!(e instanceof MessageEvent) || !(((MessageEvent) e).getMessage() instanceof HttpRequest))
    {
      super.handleUpstream(ctx, e);
      return;
    }

    HttpRequest request = (HttpRequest) ((MessageEvent) e).getMessage();

    boolean matchFound = false;
    boolean isAuthorized = false;

    for (Route route : _routes)
    {
      if (!route.isMatch(request)) continue;
      setHandler(ctx.getPipeline(), route.getChannelHandler());
      matchFound = true;
      if (!route.isAuthorized(request)) continue;
      isAuthorized = true;
      break;
    }

    if (!matchFound)
    {
      setHandler(ctx.getPipeline(), HANDLER_404);
    }
    else if (!isAuthorized)
    {
      setHandler(ctx.getPipeline(), HANDLER_401);
    }

    super.handleUpstream(ctx, e);
  }

  private void setHandler(ChannelPipeline p, ChannelHandler handler)
  {
    synchronized (p)
    {
      if (p.get(_handlerName) == null)
      {
        p.addLast(_handlerName, handler);
      }
      else
      {
        p.replace(_handlerName, _handlerName, handler);
      }
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

  protected void sendError(ChannelHandlerContext ctx, HttpResponseStatus status)
  {
    HttpResponse response = new DefaultHttpResponse(HTTP_1_1, status);
    response.setHeader(CONTENT_TYPE, "text/plain; charset=UTF-8");
    response.setContent(ChannelBuffers.copiedBuffer("Failure: " + status.toString() + "\r\n", CharsetUtil.UTF_8));

    // Close the connection as soon as the error message is sent.
    ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
  }
}
