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
      int argi = 0;
      String localhostName = args[argi++];
      Integer localhostPublicPort = Integer.parseInt(args[argi++]);
      Integer localhostAdminPort = Integer.parseInt(args[argi++]);
      String staticFileRoot = args[argi++];
      String uploadDir = args[argi++];
      
      new File(staticFileRoot).mkdir();
      new File(uploadDir).mkdir();

      liveCodeServer =
        LiveCodeServer.create(
          1024 * 1024 * 1024,
          localhostName,
          localhostPublicPort,
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
