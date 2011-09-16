package io.viper.net.server.chunkproxy;


import io.viper.net.server.Util;
import java.io.IOException;
import java.util.Map;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.util.CharsetUtil;
import org.json.JSONException;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpMethod.GET;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;


public class MappedFileServerHandler extends SimpleChannelUpstreamHandler
{
  private final Map<String, FileContentInfo> _fileMap;

  public MappedFileServerHandler(Map<String, FileContentInfo> fileMap)
  {
    _fileMap = fileMap;
  }

  public static MappedFileServerHandler create(String rootPath)
    throws IOException, JSONException
  {
    Map<String, FileContentInfo> fileMap = Util.createFileMap(rootPath);
    return new MappedFileServerHandler(fileMap);
  }

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
    throws Exception
  {
    HttpRequest request = (HttpRequest) e.getMessage();
    if (request.getMethod() != GET)
    {
      sendError(ctx, METHOD_NOT_ALLOWED);
      return;
    }

    String uri = request.getUri();

    if (!_fileMap.containsKey(uri))
    {
      sendError(ctx, NOT_FOUND);
      return;
    }

    FileContentInfo contentInfo = _fileMap.get(uri);

    DefaultHttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
    response.setHeader(Names.CONTENT_TYPE, contentInfo.meta.get(HttpHeaders.Names.CONTENT_TYPE));
    setContentLength(response, contentInfo.content.readableBytes());
    response.setContent(contentInfo.content);
    ChannelFuture writeFuture = e.getChannel().write(response);
    if (!isKeepAlive(request))
    {
      writeFuture.addListener(ChannelFutureListener.CLOSE);
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
