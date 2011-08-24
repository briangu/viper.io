package viper.app.photo;


import java.net.URISyntaxException;


public class Main
{
  public static void main(String[] args)
  {
    String awsId = args[0];
    String awsSecret = args[1];
    String bucketName = args[2];

    try
    {
      PhotoServer photoServer = PhotoServer.create(18080, awsId, awsSecret, bucketName);
    }
    catch (URISyntaxException e)
    {
      e.printStackTrace();
    }
  }
}
