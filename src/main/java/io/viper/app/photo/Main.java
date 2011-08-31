package io.viper.app.photo;


import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import org.json.JSONException;


public class Main
{
  public static void main(String[] args)
  {
    PhotoServer photoServer;

    try
    {
      if (args.length >= 3)
      {
        String awsId = args[0];
        String awsSecret = args[1];
        String bucketName = args[2];
        photoServer = PhotoServer.createWithS3(18080, awsId, awsSecret, bucketName);
      }
      else
      {
        String staticFileRoot = "/Users/bguarrac/scm/viper/src/main/resources/public";
        new File(staticFileRoot).mkdir();
        photoServer = PhotoServer.create(18080, staticFileRoot);
      }
    }
    catch (URISyntaxException e)
    {
      e.printStackTrace();
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }
    catch (JSONException e)
    {
      e.printStackTrace();
    }
  }
}
