package viper.net.server;


import javax.activation.MimetypesFileTypeMap;


/**
 * Created by IntelliJ IDEA. User: bguarrac Date: 8/18/11 Time: 5:29 PM To change this template use File | Settings |
 * File Templates.
 */
public class Util
{
  static private MimetypesFileTypeMap _fileTypeMap = new MimetypesFileTypeMap();

  public static String getContentType(String filename)
  {
    if (filename == null || filename.length() == 0)
    {
      return "application/octet-stream";
    }

    String contentType;

    if (filename.endsWith(".js"))
    {
      contentType = "text/javascript";
    }
    else if (filename.endsWith(".css"))
    {
      contentType = "text/css";
    }
    else
    {
      contentType = _fileTypeMap.getContentType(filename);
    }

    return contentType;
  }
}
