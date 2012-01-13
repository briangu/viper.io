package io.viper.core.server.file;


import static org.jboss.netty.handler.codec.http.HttpHeaders.*;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.*;
import static org.jboss.netty.handler.codec.http.HttpMethod.*;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;
import static org.jboss.netty.handler.codec.http.HttpVersion.*;

import io.viper.core.server.Util;
import io.viper.core.server.router.RouteHandler;
import io.viper.core.server.router.RouteResponse;
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

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;


public class StaticFileServerHandler implements RouteHandler
{
  private final FileContentInfoProvider _fileCache;

  public StaticFileServerHandler(FileContentInfoProvider fileCache)
  {
    _fileCache = fileCache;
  }

  @Override
  public RouteResponse exec(Map<String, String> args)
  {
    DefaultHttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);

    final String filePath;

    if (!args.containsKey("path"))
    {
      response.setStatus(NOT_FOUND);
      return new RouteResponse(response);
    }

    try
    {
      filePath = Util.sanitizeUri(args.get("path"));
    }
    catch (URISyntaxException e)
    {
      e.printStackTrace();
      response.setStatus(NOT_FOUND);
      return new RouteResponse(response);
    }

    final FileContentInfo contentInfo = _fileCache.getFileContent(filePath);

    if (contentInfo != null)
    {
      response.setHeader(HttpHeaders.Names.CONTENT_TYPE, contentInfo.meta.get(Names.CONTENT_TYPE));
      response.setContent(contentInfo.content);

      return new RouteResponse(response, new RouteResponse.RouteResponseDispose(){
        @Override
        public void dispose()
        {
          _fileCache.dispose(contentInfo);
        }
      });
    }
    else
    {
      response.setStatus(NOT_FOUND);
      return new RouteResponse(response);
    }
  }
}
