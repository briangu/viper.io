package io.viper.core.server.router;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RouteUtil
{
  public static List<String> parsePath(String path)
  {
    return Arrays.asList(path.split("//"));
  }

  public static boolean match(List<String> route, List<String> path)
  {
    if (path.size() < route.size()) return false;
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
        args.put(varName, join(path.subList(i, path.size()-1), "/"));
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

    sb.append(s.get(s.size()-1));

    return sb.toString();
  }
}
