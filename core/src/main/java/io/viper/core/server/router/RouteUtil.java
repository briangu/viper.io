package io.viper.core.server.router;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RouteUtil
{
  public static List<String> parsePath(String path)
  {
    int queryParamStart = path.indexOf("?");

    if (queryParamStart > 0)
    {
      path = path.substring(0, path.indexOf("?"));
    }

    String[] parts = path.split("\\/");

    List<String> parsedPath = new ArrayList<String>();

    for (String part : parts)
    {
      if (part.isEmpty()) continue;
      parsedPath.add(part);
    }

    return parsedPath;
  }

  public static Map<String, String> extractQueryParams(URI uri)
    throws UnsupportedEncodingException
  {
    return extractQueryParams(uri.getQuery());
  }

  public static Map<String, String> extractQueryParams(String queryString)
    throws UnsupportedEncodingException
  {
    Map<String, String> map = new HashMap<String, String>();
    
    if (queryString != null)
    {
      String[] parts = queryString.split("&");
      for (String part : parts)
      {
        String[] pair = part.split("=");
        if (pair.length != 2) continue;
        map.put(pair[0], URLDecoder.decode(pair[1], "UTF-8"));
      }
    }

    return map;
  }
  
  public static boolean match(List<String> route, List<String> path)
  {
    if (path.size() < route.size()) return false;
    if (path.size() > route.size() && route.size() == 0) return false;
    if ((path.size() > route.size()) && (!route.get(route.size()-1).startsWith("$"))) return false;

    for (int i = 0; i < route.size(); i++)
    {
      if (route.get(i).startsWith("$")) continue;
      if (route.get(i).equals(path.get(i))) continue;
      return false;
    }

    return true;
  }


  public static Map<String, String> extractPathArgs(List<String> route, List<String> path)
  {
    Map<String, String> args = new HashMap<String, String>();

    for (int i = 0; i < route.size(); i++)
    {
      if (!route.get(i).startsWith("$")) continue;

      String varName = route.get(i).substring(1);

      if (i < route.size()-1)
      {
        args.put(varName, path.get(i));
      }
      else
      {
        args.put(varName, join(path.subList(i, path.size()), "/"));
      }
    }

    return args;
  }

  private static String join(List<String> s, String delim)
  {
    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < s.size() - 1; i++)
    {
      sb.append(s.get(i));
      sb.append(delim);
    }

    if (s.size() > 0) sb.append(s.get(s.size()-1));

    return sb.toString();
  }
}
