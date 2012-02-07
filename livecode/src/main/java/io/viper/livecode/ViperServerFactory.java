package io.viper.livecode;

import io.viper.core.server.router.*;
import javassist.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ViperServerFactory
{
  public static ViperServer create(
    int maxContentLength,
    String localhostName,
    int localhostPort,
    JSONObject serverDef)
      throws JSONException
  {
    if (!serverDef.has("host")) throw new IllegalArgumentException("missing host name in serverDef");

    String hostName = serverDef.getString("host");
    
    String staticFileRoot = serverDef.has("staticFileRoot") ? serverDef.getString("staticFileRoot") : null;

    List<Route> routes;

    if (serverDef.has("routes"))
    {
      routes = loadRoutes(hostName, serverDef.getJSONArray("routes"));
    }
    else
    {
      routes = new ArrayList<Route>();
    }

    ViperServer viperServer = new ViperServer(
      maxContentLength,
      localhostName,
      localhostPort,
      staticFileRoot,
      routes);

    return viperServer;
  }

  private static List<Route> loadRoutes(String hostName, JSONArray routeDefs) throws JSONException
  {
    List<Route> routes = new ArrayList<Route>();
    
    for (int i = 0; i < routeDefs.length(); i++)
    {
      routes.add(loadRoute(hostName, routeDefs.getJSONObject(i)));
    }

    return routes;
  }

  private static Route loadRoute(String hostName, JSONObject routeDef) throws JSONException
  {
    if (!routeDef.has("method")) throw new IllegalArgumentException("missing method field in route: " + routeDef.toString());
    if (!routeDef.has("path")) throw new IllegalArgumentException("missing path field in route: " + routeDef.toString());
    if (!routeDef.has("routeHandler")) throw new IllegalArgumentException("missing routeHandler field in route: " + routeDef.toString());

    String method = routeDef.getString("method");
    String path = routeDef.getString("path");

    // TODO: routeHandler should be a block reference in cloudcmd
    // we should load the block from cloudcmd instead of using the raw embedded java in the json
    String rawRouteHandler = routeDef.getString("routeHandler");

    String classId = hostName + ":" + method + ":" + path;

    RouteHandler routeHandler = compileRouteHandler(classId, rawRouteHandler);

    return createRestRoute(method, path, routeHandler);
  }

  static RouteListFactory compileRouteListFactory(final String sourcePath) {
    try {
      final CharStringCompiler stringCompiler = ToolProvider.getSystemJavaCompiler();
      final String qName = null;
      final String source = FileUtil.readFile(sourcePath);
      // compile the generated Java source
      final DiagnosticCollector<JavaFileObject> errs = new DiagnosticCollector<JavaFileObject>();
      Class<RouteListFactory> compiledFunction = stringCompiler.compile(qName, source, errs,
        new Class<?>[] { RouteListFactory.class });
      return compiledFunction.newInstance();
    } catch (InstantiationException e) {
    } catch (IllegalAccessException e) {
    } catch (IOException e) {
    }
    return NULL_FUNCTION;
  }

  private static RouteHandler fancyCompileRouteHandler()
  {
    //Compiler.compileClasses("");

    File root = new File(_liveCodeRoot + File.separator + host);
    File sourceFile = new File(root, classId);
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    compiler.run(null, null, null, sourceFile.getPath());

    RouteHandler routeHandler;

    try
    {
      ClassPool pool = ClassPool.getDefault();
      CtClass ctClass = pool.makeClass(classId);
      Object newInstance = ctClass.toClass().newInstance();
      if (!(newInstance instanceof RouteListFactory))
      {
        throw new IllegalArgumentException("class does not extend RouteListFactory");
      }
      routeHandler = (RouteListFactory)newInstance;
    }
    catch (CannotCompileException e)
    {
      throw new IllegalArgumentException(classId + " => cannot compile routeHandler: " + e.getMessage());
    }
    catch (IllegalAccessException e)
    {
      throw new IllegalArgumentException(classId + " => cannot compile routeHandler: " + e.getMessage());
    }
    catch (InstantiationException e)
    {
      throw new IllegalArgumentException(classId + " => cannot instantiate routeHandler: " + e.getMessage());
    }

    return routeHandler;
  }
  
  private static RouteHandler compileRouteHandler(
    String classId,
    String rawRouteHandler) 
  {
    RouteHandler routeHandler;

    try
    {
      ClassPool pool = ClassPool.getDefault();
      CtClass ctClass = pool.makeClass(classId);
      ctClass.setInterfaces(new CtClass[] { pool.makeClass("RouteHandler") });
      CtMethod ctMethod = CtNewMethod.make("public RouteResponse exec(Map<String, String> args) throws Exception { return null; }", ctClass);
      ctMethod.setBody(rawRouteHandler);
      ctClass.addMethod(ctMethod);
      Object newInstance = ctClass.toClass().newInstance();
      routeHandler = (RouteHandler)newInstance;
    }
    catch (CannotCompileException e)
    {
      throw new IllegalArgumentException(classId + " => cannot compile routeHandler: " + e.getMessage());
    }
    catch (IllegalAccessException e)
    {
      throw new IllegalArgumentException(classId + " => cannot compile routeHandler: " + e.getMessage());
    }
    catch (InstantiationException e)
    {
      throw new IllegalArgumentException(classId + " => cannot instantiate routeHandler: " + e.getMessage());
    }

    return routeHandler;
  }
  
  private static RestRoute createRestRoute(String method, String path, RouteHandler routeHandler)
  {
    if (method.equals("GET"))
    {
      return new GetRoute(path, routeHandler);
    }
    else if (method.equals("POST"))
    {
      return new PostRoute(path, routeHandler);
    }
    else if (method.equals("PUT"))
    {
      return new PutRoute(path, routeHandler);
    }
    else if (method.equals("DELETE"))
    {
      return new DeleteRoute(path, routeHandler);
    }
    
    throw new IllegalArgumentException("unknown method type: " + method);
  }
}
