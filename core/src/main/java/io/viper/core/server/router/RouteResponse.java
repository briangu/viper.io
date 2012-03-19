package io.viper.core.server.router;


import org.jboss.netty.handler.codec.http.HttpResponse;


public class RouteResponse
{
  public HttpResponse HttpResponse;
  private RouteResponseDispose DisposeHandler = null;

  public RouteResponse()
  {
  }

  public interface RouteResponseDispose
  {
    void dispose();
  }

  public RouteResponse(HttpResponse response)
  {
    HttpResponse = response;
  }

  public RouteResponse(HttpResponse response, RouteResponseDispose disposeHandler)
  {
    HttpResponse = response;
    DisposeHandler = disposeHandler;
  }

  public void dispose()
  {
    if (DisposeHandler != null)
    {
      DisposeHandler.dispose();
    }
  }
}
