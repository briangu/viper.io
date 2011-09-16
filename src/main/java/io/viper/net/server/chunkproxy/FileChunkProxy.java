package io.viper.net.server.chunkproxy;


import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.Map;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.json.JSONException;
import org.json.JSONObject;


public class FileChunkProxy implements HttpChunkRelayProxy
{
  private final String _rootDir;

  String _filePath;
  String _metaFilePath;
  RandomAccessFile _raf;
  FileChannel _fileChannel;
  Map<String, String> _objectMeta;
  String _objectName;

  HttpChunkProxyEventListener _listener;

  private enum State
  {
    relay,
    closed
  }

  private State _state = State.closed;

  public FileChunkProxy(String rootDir)
  {
    _rootDir = rootDir;
    _metaFilePath = _rootDir + File.separatorChar + ".meta" + File.separatorChar;
    new File(_metaFilePath).mkdir();
  }

  @Override
  public boolean isRelaying()
  {
    return _state.equals(State.relay);
  }

  private String createFilePath(String filename)
  {
    return String.format("%s%s%s", _rootDir, File.separator, filename);
  }

  @Override
  public void init(
    HttpChunkProxyEventListener listener,
    String objectName,
    Map<String, String> objectMeta,
    long objectSize)
      throws Exception
  {
    if (!_state.equals(State.closed))
    {
      throw new IllegalStateException("init cannot be called before complete or abort");
    }

    _listener = listener;

    _filePath = createFilePath(objectName);
    _raf = new RandomAccessFile(_filePath, "rw");
    _raf.setLength(objectSize);
    _fileChannel = _raf.getChannel();
    _objectMeta = objectMeta;
    _objectName = objectName;

    _state = State.relay;

    _listener.onProxyConnected();
    _listener.onProxyWriteReady();
  }

  @Override
  public void writeChunk(HttpChunk chunk)
  {
    if (!_state.equals(State.relay))
    {
      throw new IllegalStateException("init must be called first");
    }

    try
    {
      if (chunk.isLast())
      {
        _fileChannel.write(chunk.getContent().toByteBuffer());
        _fileChannel.close();

        if (_objectMeta.containsKey(HttpHeaders.Names.CONTENT_TYPE))
        {
          File meta = new File(_metaFilePath + _objectName);
          RandomAccessFile metaRaf = new RandomAccessFile(meta, "rw");
          JSONObject jsonObject = new JSONObject(_objectMeta);
          metaRaf.writeUTF(jsonObject.toString(2));
          metaRaf.close();
        }

        _state = State.closed;
        _listener.onProxyCompleted();
        reset();
      }
      else
      {
        _fileChannel.write(chunk.getContent().toByteBuffer());
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
      _listener.onProxyError();
      abort();
    }
  }

  @Override
  public void abort()
  {
    if (!_state.equals(State.relay))
    {
      throw new IllegalStateException("init must be called first");
    }
    _listener.onProxyError();
    new File(_filePath).delete();
    reset();
  }

  private void reset()
  {
    _filePath = null;
    _raf = null;
    _fileChannel = null;
    _listener = null;
  }
}

