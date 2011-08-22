package viper.net.server.chunkproxy;


import static org.jboss.netty.handler.codec.http.HttpHeaders.*;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.*;
import static org.jboss.netty.handler.codec.http.HttpMethod.*;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;
import static org.jboss.netty.handler.codec.http.HttpVersion.*;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.buffer.ChannelBuffer;
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
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.jboss.netty.util.CharsetUtil;

import javax.activation.MimetypesFileTypeMap;
import java.io.*;
import java.net.*;
import viper.net.server.CachableHttpResponse;
import viper.net.server.Util;


/**
 * A file server that can serve files from file system and class path.
 * <p/>
 * If you wish to customize the error message, please sub-class and override sendError(). Based on Trustin Lee's
 * original file serving example
 */
public class StaticFileServerHandler extends SimpleChannelUpstreamHandler
{
  private String _rootPath;
  private String _stripFromUri;
  private int _cacheMaxAge = -1;
  private boolean _fromClasspath = false;

  private final File _indexFile;

  // TODO: add support for index.htm

  public StaticFileServerHandler(String path)
  {
    if (path.startsWith("classpath://"))
    {
      _fromClasspath = true;
      _rootPath = path.replace("classpath://", "");
      if (_rootPath.lastIndexOf("/") == _rootPath.length() - 1)
      {
        _rootPath = _rootPath.substring(0, _rootPath.length() - 1);
      }
    }
    else
    {
      _rootPath = path;
    }
    _rootPath = _rootPath.replace(File.separatorChar, '/');

    _indexFile = getIndexFile(_rootPath);
  }

  public StaticFileServerHandler(String path, String stripFromUri)
  {
    this(path);
    this._stripFromUri = stripFromUri;
  }

  public StaticFileServerHandler(String path, int cacheMaxAge)
  {
    this(path);
    this._cacheMaxAge = cacheMaxAge;
  }

  public StaticFileServerHandler(String path, int cacheMaxAge, String stripFromUri)
  {
    this(path, cacheMaxAge);
    this._stripFromUri = stripFromUri;
  }

  private static File getIndexFile(String rootPath)
  {
    File foundIndex = null;

    final String[] defaultFiles = new String[] { "index.html", "index.htm" };

    for (String defaultFile : defaultFiles)
    {
      File tmpFile = new File(rootPath + "/" + defaultFile);
      if (tmpFile.exists())
      {
        foundIndex = tmpFile;
        break;
      }
    }

    return foundIndex;
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
    if (_stripFromUri != null)
    {
      uri = uri.replaceFirst(_stripFromUri, "");
    }

    final String path = Util.sanitizeUri(uri);
    if (path == null)
    {
      sendError(ctx, FORBIDDEN);
      return;
    }

    FileContentInfo contentInfo = getFileContent(path);
    if (contentInfo == null)
    {
      sendError(ctx, NOT_FOUND);
      return;
    }

    CachableHttpResponse response = new CachableHttpResponse(HTTP_1_1, OK);
    response.setHost(request.getHeader("Host"));
    response.setRequestUri(request.getUri());
    response.setCacheMaxAge(_cacheMaxAge);
    response.setHeader(HttpHeaders.Names.CONTENT_TYPE, contentInfo.contentType);
    setContentLength(response, contentInfo.content.readableBytes());

    response.setBackingFileChannel(contentInfo.fileChannel);
    response.setContent(contentInfo.content);
    ChannelFuture writeFuture = e.getChannel().write(response);

    // Decide whether to close the connection or not.
    if (!isKeepAlive(request))
    {
      // Close the connection when the whole content is written out.
      writeFuture.addListener(ChannelFutureListener.CLOSE);
    }
  }

  private class FileContentInfo
  {
    public ChannelBuffer content;
    public FileChannel fileChannel;
    public String contentType;

    public FileContentInfo(FileChannel fileChannel, ChannelBuffer content, String contentType)
    {
      this.fileChannel = fileChannel;
      this.content = content;
      this.contentType = contentType;
    }
  }

  private FileContentInfo getFileContent(String path)
  {
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
        file = new File(_rootPath + path);
      }

      String contentType;

      if (!file.exists() || path.equals("/"))
      {
        file = path.equals("/") ? _indexFile : null;
        if (file == null)
        {
          return null;
        }
        contentType = "text/html";
      }
      else
      {
        contentType = Util.getContentType(path);
      }

      fc = new RandomAccessFile(file, "r").getChannel();
      ByteBuffer roBuf = fc.map(FileChannel.MapMode.READ_ONLY, 0, (int) fc.size());
      result = new FileContentInfo(fc, ChannelBuffers.wrappedBuffer(roBuf), contentType);
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
