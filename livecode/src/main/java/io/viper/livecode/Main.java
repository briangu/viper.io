package io.viper.livecode;


import java.io.File;


public class Main
{
  public static void main(String[] args)
  {
    LiveCodeServer liveCodeServer = null;

    try
    {
      String localHostName = args[0];
      Integer localHostPort = Integer.parseInt(args[1]);
      String staticFileRoot = args[2];
      String uploadDir = args[3];
      
      new File(staticFileRoot).mkdir();
      new File(uploadDir).mkdir();

      liveCodeServer =
        LiveCodeServer.create(
          1024 * 1024 * 1024,
          localHostName,
          localHostPort,
          staticFileRoot,
          uploadDir);

      try
      {
        Thread.currentThread().join();
      }
      catch (InterruptedException e)
      {
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    finally
    {
      if (liveCodeServer != null)
      {
        liveCodeServer.shutdown();
      }
    }
  }
}
