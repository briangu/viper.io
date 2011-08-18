package viper.net.app.photo;


public class Main
{
  public static void main(String[] args)
  {
    String awsId = args[0];
    String awsSecret = args[1];
    String bucketName = args[2];

    PhotoServer photoServer = PhotoServer.create(8080, awsId, awsSecret, bucketName);
  }
}
