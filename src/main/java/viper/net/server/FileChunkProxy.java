package viper.net.server;


import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import org.jboss.netty.handler.codec.http.HttpChunk;


public class FileChunkProxy implements HttpChunkRelayProxy
{
  private final String _rootDir;

  String _filePath;
  RandomAccessFile _raf;
  FileChannel _fileChannel;

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
    new File(rootDir).mkdir();
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
  public void init(HttpChunkProxyEventListener listener, String objectName, long objectSize)
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
    _state = State.relay;
    _listener.onProxyReady();
  }

  @Override
  public void appendChunk(HttpChunk chunk)
  {
    if (!_state.equals(State.relay))
    {
      throw new IllegalStateException("init must be called first");
    }

    try
    {
      _fileChannel.write(chunk.getContent().toByteBuffer());
    }
    catch (IOException e)
    {
      _listener.onProxyError();
      abort();
    }
  }

  @Override
  public void complete(HttpChunk chunk)
  {
    if (!_state.equals(State.relay))
    {
      throw new IllegalStateException("init must be called first");
    }

    try
    {
      _fileChannel.write(chunk.getContent().toByteBuffer());
      _fileChannel.close();
      _state = State.closed;
      _listener.onProxyCompleted();
      reset();
    }
    catch (IOException e)
    {
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

