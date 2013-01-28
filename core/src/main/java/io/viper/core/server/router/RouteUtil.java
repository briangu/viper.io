package io.viper.core.server.router;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.*;

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
    return extractQueryParams(queryString, true);
  }

  public static Map<String, String> extractQueryParams(String queryString, Boolean utf8Decode)
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
        // TODO: we should only urldecode iff the incoming encoding calls for it
        map.put(pair[0], utf8Decode ? URLDecoder.decode(pair[1], "UTF-8") : pair[1]);
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


  public static Map<String, String> extractArgs(HttpRequest request, List<String> route, List<String> path)
    throws URISyntaxException, UnsupportedEncodingException, JSONException
  {
    Map<String, String> args = RouteUtil.extractPathArgs(route, path);
    args.putAll(RouteUtil.extractQueryParams(new URI(request.getUri())));

    ChannelBuffer content = request.getContent();
    if (!content.hasArray())
    {
      return null;
    }

    byte[] body = content.array();
    String contentLengthHeader = request.getHeader(HttpHeaders.Names.CONTENT_LENGTH);

    int contentLength =
      contentLengthHeader != null
        ? Integer.parseInt(request.getHeader(HttpHeaders.Names.CONTENT_LENGTH))
        : body.length;

    if (contentLength != body.length) {
      body = Arrays.copyOfRange(body, 0, contentLength);
    }

    String rawContent = new String(body, "UTF-8");

    if (rawContent.startsWith("{"))
    {
      JSONObject json = new JSONObject(rawContent);

      Iterator keys = json.keys();
      while(keys.hasNext())
      {
        String key = keys.next().toString();
        args.put(key, json.getString(key));
      }
    }
    else
    {
      args.putAll(RouteUtil.extractQueryParams(rawContent));
    }

    return args;
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
