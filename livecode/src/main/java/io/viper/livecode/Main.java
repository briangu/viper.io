package io.viper.livecode;


import java.io.File;
import java.util.concurrent.Executors;


public class Main
{
  public static void main(String[] args)
  {
    LiveCodeServer liveCodeServer = null;

    try
    {
      String localhostName = args[0];
      Integer localhostAdminPort = Integer.parseInt(args[1]);
      String staticFileRoot = args[2];
      String uploadDir = args[3];
      
      new File(staticFileRoot).mkdir();
      new File(uploadDir).mkdir();

      liveCodeServer =
        LiveCodeServer.create(
          1024 * 1024 * 1024,
          localhostName,
          localhostAdminPort,
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
