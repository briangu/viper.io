package viper.net.server.chunkproxy;


import static org.jboss.netty.handler.codec.http.HttpHeaders.*;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.*;
import static org.jboss.netty.handler.codec.http.HttpMethod.*;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;
import static org.jboss.netty.handler.codec.http.HttpVersion.*;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import javax.ws.rs.Path;
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

import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

import viper.net.server.Util;


/**
 * A file server that can serve files from file system and class path.
 * <p/>
 * If you wish to customize the error message, please sub-class and override sendError().
 * Based on Trustin Lee's original file serving example
 */
public class StaticFileServerHandler extends SimpleChannelUpstreamHandler
{
  private String _rootPath;
  private final boolean _fromClasspath;
  private final ConcurrentHashMap<String, FileContentInfo> _fileCache;
  private final FileContentInfo _defaultFile;
  String _metaFilePath;

  public StaticFileServerHandler(
      String rootPath,
      ConcurrentHashMap<String, FileContentInfo> fileCache,
      FileContentInfo defaultFile)
  {
    _fileCache = fileCache;
    _fromClasspath = rootPath.startsWith("classpath://");
    _defaultFile = defaultFile;

    if (_fromClasspath)
    {
      _rootPath = rootPath.replace("classpath://", "");
      if (_rootPath.lastIndexOf("/") == _rootPath.length() - 1)
      {
        _rootPath = _rootPath.substring(0, _rootPath.length() - 1);
      }
    }
    else
    {
      _rootPath = rootPath;
    }

    _rootPath = _rootPath.replace(File.separatorChar, '/');
    _metaFilePath = _rootPath + File.separatorChar + ".meta" + File.separatorChar;
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

    final String path = Util.sanitizeUri(uri);
    if (path == null)
    {
      sendError(ctx, FORBIDDEN);
      return;
    }

    FileContentInfo contentInfo;

    if (_fileCache.containsKey(path))
    {
      contentInfo = _fileCache.get(path);
    }
    else
    {
      synchronized (_fileCache)
      {
        if (!_fileCache.containsKey(path))
        {
          contentInfo = getFileContent(path);
          if (contentInfo == null)
          {
            sendError(ctx, NOT_FOUND);
            return;
          }

          _fileCache.put(path, contentInfo);
        }
        else
        {
          contentInfo = _fileCache.get(path);
        }
      }
    }

    DefaultHttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
    response.setHeader(HttpHeaders.Names.CONTENT_TYPE, contentInfo.contentType);
    setContentLength(response, contentInfo.content.readableBytes());
    response.setContent(contentInfo.content);
    ChannelFuture writeFuture = e.getChannel().write(response);

    if (!isKeepAlive(request))
    {
      writeFuture.addListener(ChannelFutureListener.CLOSE);
    }
  }

  private FileContentInfo getFileContent(String path)
  {
    if (path.equals("/"))
    {
      return _defaultFile;
    }

    FileChannel fc = null;
    FileContentInfo result = null;

    try
    {
      File file;

      if (_fromClasspath)
      {
        URL url = this.getClass().getResource(_rootPath + path);
        if (url == null)
        {
          return null;
        }
        file = new File(url.getFile());
      }
      else
      {
        // TODO: make a touch more secure - chroot to the rescue!
        file = new File(_rootPath + path);
      }

      if (file.exists())
      {
        String contentType;

        File meta = new File(_metaFilePath + path);
        if (meta.exists())
        {
          RandomAccessFile metaRaf = new RandomAccessFile(meta, "r");
          contentType = metaRaf.readUTF();
          metaRaf.close();
        }
        else
        {
          contentType = Util.getContentType(path);
        }

        fc = new RandomAccessFile(file, "r").getChannel();
        ByteBuffer roBuf = fc.map(FileChannel.MapMode.READ_ONLY, 0, (int) fc.size());
        result = new FileContentInfo(fc, ChannelBuffers.wrappedBuffer(roBuf), contentType);
      }
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }
    finally
    {
      if (result == null)
      {
        if (fc != null)
        {
          try
          {
            fc.close();
          }
          catch (IOException e)
          {
            e.printStackTrace();
          }
        }
      }
    }

    return result;
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
