package io.viper.core.common;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.NameValuePair;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SchemeSocketFactory;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.json.JSONException;
import org.json.JSONObject;


public class HttpJSONClient
{
  String _scheme;
  String _host;
  int _port;
  String _path;
  int _defaultKeepAliveDurationMS;
  int _maxRetries;
  DefaultHttpClient _httpclient;
  TrustManager _trustManager;

  public static HttpJSONClient create(String url)
      throws IOException, NoSuchAlgorithmException, KeyManagementException
  {
    URL urlObj = new URL(url);

    return new HttpJSONClient(
      urlObj.getProtocol(),
      urlObj.getHost(),
      urlObj.getPort(),
      urlObj.getPath());
  }

  public HttpJSONClient(String scheme, String host, int port, String path)
    throws NoSuchAlgorithmException, IOException, KeyManagementException
  {
    this(
      scheme,
      host,
      port,
      path,
      5000,
      5);
  }

  public HttpJSONClient(String scheme,
                        String host,
                        int port,
                        String path,
                        int defaultKeepAliveDurationMS,
                        final int maxRetries)
    throws NoSuchAlgorithmException, IOException, KeyManagementException
  {
    this(scheme,
         host,
         port,
         path,
         defaultKeepAliveDurationMS,
         maxRetries,
         new X509TrustManager() {

           @Override
           public void checkClientTrusted(java.security.cert.X509Certificate[] x509Certificates, String s)
             throws java.security.cert.CertificateException
           {
           }

           @Override
           public void checkServerTrusted(java.security.cert.X509Certificate[] x509Certificates, String s)
             throws java.security.cert.CertificateException
           {
           }

           @Override
           public java.security.cert.X509Certificate[] getAcceptedIssuers()
           {
             return new java.security.cert.X509Certificate[0];
           }
         },
         null);
  }

  public HttpJSONClient(String scheme,
                        String host,
                        int port,
                        String path,
                        int defaultKeepAliveDurationMS,
                        final int maxRetries,
                        TrustManager trustManager,
                        HttpRequestRetryHandler retryHandler)
    throws NoSuchAlgorithmException, IOException, KeyManagementException
  {
    _scheme = scheme;
    _host = host;
    _port = port;
    _path = path;
    _defaultKeepAliveDurationMS = defaultKeepAliveDurationMS;
    _maxRetries = maxRetries;
    _trustManager = trustManager;
    _httpclient = createHttpClient(retryHandler);
  }

  private DefaultHttpClient createHttpClient(HttpRequestRetryHandler retryHandler)
    throws NoSuchAlgorithmException, KeyManagementException, IOException
  {
    HttpParams params = new BasicHttpParams();
    params.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 1000);

    SchemeRegistry registry = new SchemeRegistry();
    SchemeSocketFactory sf;

    if (_scheme.equals("https"))
    {
      SSLContext sslcontext = SSLContext.getInstance("TLS");
      sslcontext.init(null, new TrustManager[] { _trustManager }, null);
      sf = new SSLSocketFactory(sslcontext);
    }
    else
    {
      sf = PlainSocketFactory.getSocketFactory();
    }

    registry.register(new Scheme(_scheme, _port, sf));

    ClientConnectionManager cm = new ThreadSafeClientConnManager(registry);
    DefaultHttpClient client = new DefaultHttpClient(cm, params);
    if (retryHandler == null)
    {
      retryHandler = new HttpRequestRetryHandler()
      {
        public boolean retryRequest(IOException exception, int executionCount, HttpContext context)
        {
          if (executionCount >= _maxRetries)
          {
            // Do not retry if over max retry count
            return false;
          }
          if (exception instanceof NoHttpResponseException)
          {
            // Retry if the server dropped connection on us
            return true;
          }
          if (exception instanceof SSLHandshakeException)
          {
            // Do not retry on SSL handshake exception
            return false;
          }
          HttpRequest request = (HttpRequest) context.getAttribute(ExecutionContext.HTTP_REQUEST);
          boolean idempotent = !(request instanceof HttpEntityEnclosingRequest);
          if (idempotent)
          {
            // Retry if the request is considered idempotent
            return true;
          }
          return false;
        }
      };
    }
    client.setHttpRequestRetryHandler(retryHandler);

    client.addRequestInterceptor(new HttpRequestInterceptor()
    {
      public void process(final HttpRequest request, final HttpContext context)
        throws HttpException, IOException
      {
        if (!request.containsHeader("Accept-Encoding"))
        {
          request.addHeader("Accept-Encoding", "gzip");
        }
      }
    });

    client.addResponseInterceptor(new HttpResponseInterceptor()
    {
      public void process(final HttpResponse response, final HttpContext context)
        throws HttpException, IOException
      {
        HttpEntity entity = response.getEntity();
        Header ceheader = entity.getContentEncoding();
        if (ceheader != null)
        {
          HeaderElement[] codecs = ceheader.getElements();
          for (int i = 0; i < codecs.length; i++)
          {
            if (codecs[i].getName().equalsIgnoreCase("gzip"))
            {
              response.setEntity(new GzipDecompressingEntity(response.getEntity()));
              return;
            }
          }
        }
      }
    });

    client.setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy()
    {
      @Override
      public long getKeepAliveDuration(HttpResponse response, HttpContext context)
      {
        // Honor 'keep-alive' header
        HeaderElementIterator it = new BasicHeaderElementIterator(response.headerIterator(HTTP.CONN_KEEP_ALIVE));
        while (it.hasNext())
        {
          HeaderElement he = it.nextElement();
          String param = he.getName();
          String value = he.getValue();
          if ((value != null) && param.equalsIgnoreCase("timeout"))
          {
            try
            {
              return Long.parseLong(value) * 1000;
            }
            catch (NumberFormatException ignore)
            {
            }
          }
        }

        long keepAlive = super.getKeepAliveDuration(response, context);
        if (keepAlive == -1)
        {
          keepAlive = _defaultKeepAliveDurationMS;
        }
        return keepAlive;
      }
    });

    return client;
  }

  private static class GzipDecompressingEntity extends HttpEntityWrapper
  {
    public GzipDecompressingEntity(final HttpEntity entity)
    {
      super(entity);
    }

    @Override
    public InputStream getContent()
      throws IOException, IllegalStateException
    {
      // the wrapped entity's getContent() decides about repeatability
      InputStream wrappedin = wrappedEntity.getContent();
      return new GZIPInputStream(wrappedin);
    }

    @Override
    public long getContentLength()
    {
      // length of ungzipped content is not known
      return -1;
    }

  }

  public JSONObject doQuery(List<NameValuePair> queryParams)
    throws URISyntaxException, IOException, JSONException
  {
    JSONObject result;
    InputStream is = null;

    try
    {
      URI requestURI = buildRequestURI(queryParams);
      is = makeGetRequest(requestURI);
      result = convertStreamToJSONObject(is);
    }
    finally
    {
      if (is != null)
      {
    	  IOUtils.closeQuietly(is);
      }
    }

    return result;
  }

  public JSONObject doPost(List<NameValuePair> nameValuePairs)
    throws URISyntaxException, IOException, JSONException
  {
    return doPost(nameValuePairs, new HashMap<String, String>());
  }

  public JSONObject doPost(List<NameValuePair> nameValuePairs, Map<String,String> headers)
    throws URISyntaxException, IOException, JSONException
  {
    JSONObject result;
    InputStream is = null;

    try
    {
      URI requestURI = buildRequestURI();
      is = makePostRequest(requestURI, nameValuePairs, headers);
      result = convertStreamToJSONObject(is);
    }
    finally
    {
      if (is != null)
      {
    	  IOUtils.closeQuietly(is);
      }
    }

    return result;
  }

  public JSONObject doPost(String data, Map<String,String> headers)
    throws URISyntaxException, IOException, JSONException
  {
    JSONObject result;
    InputStream is = null;

    try
    {
      URI requestURI = buildRequestURI();
      is = makePostRequest(requestURI, data, headers);
      result = convertStreamToJSONObject(is);
    }
    finally
    {
      if (is != null)
      {
    	  IOUtils.closeQuietly(is);
      }
    }

    return result;
  }

  public URI buildRequestURI()
      throws URISyntaxException
  {
    return buildRequestURI(new ArrayList<NameValuePair>());
  }

  public URI buildRequestURI(List<NameValuePair> qparams)
      throws URISyntaxException
  {
    URI uri =
      URIUtils.createURI(
        _scheme,
        _host,
        _port,
        _path,
        URLEncodedUtils.format(qparams, "UTF-8"),
        null);
    return uri;
  }

  public InputStream makeGetRequest(URI uri)
      throws IOException
  {
	  System.out.println("getting: "+uri);
    HttpGet httpget = new HttpGet(uri);
    HttpResponse response = _httpclient.execute(httpget);
    HttpEntity entity = response.getEntity();
    if (entity == null)
    {
      throw new IOException("failed to complete request");
    }

    return entity.getContent();
  }

  public InputStream makePostRequest(URI uri, List<NameValuePair> nameValuePairs)
      throws IOException
  {
    return makePostRequest(uri, nameValuePairs, new HashMap<String, String>());
  }

  public InputStream makePostRequest(URI uri, List<NameValuePair> nameValuePairs, Map<String,String> headers)
      throws IOException
  {
    return makePostRequest(uri, new UrlEncodedFormEntity(nameValuePairs), headers);
  }

  public InputStream makePostRequest(URI uri, String data, Map<String,String> headers)
      throws IOException
  {
    return makePostRequest(uri, new StringEntity(data), headers);
  }

  public InputStream makePostRequest(URI uri, HttpEntity entity, Map<String,String> headers)
      throws IOException
  {
	  System.out.println("posting: "+uri);
    HttpPost httppost = new HttpPost(uri);
    httppost.setEntity(entity);

    for (String key : headers.keySet())
    {
      httppost.setHeader(key, headers.get(key));
    }

    HttpResponse response = _httpclient.execute(httppost);
    HttpEntity responseEntity = response.getEntity();
    if (responseEntity == null)
    {
      throw new IOException("failed to complete request");
    }

    return responseEntity.getContent();
  }

  public static String join(String[] arr, String delimiter) {
    return join(Arrays.asList(arr), delimiter);
  }

  public static String join(Collection<?> s, String delimiter) {
    StringBuilder builder = new StringBuilder();
    Iterator iter = s.iterator();
    while (iter.hasNext()) {
       builder.append(iter.next().toString());
       if (!iter.hasNext()) {
         break;
       }
       builder.append(delimiter);
    }
    return builder.toString();
  }

  public static String convertStreamToString(InputStream is)
      throws IOException
  {
    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
    StringBuilder sb = new StringBuilder();
    char[] buf = new char[1024];  //1k buffer
     try
    {
      while(true){
    	  int count = reader.read(buf);
    	  if (count<0) break;
    	  sb.append(buf, 0, count);
      }
    }
    finally
    {
      is.close();
    }

    String json = sb.toString();
//    System.out.println("received: "+json);
    return json;
  }

  public static JSONObject convertStreamToJSONObject(InputStream is)
      throws IOException, JSONException
  {
    String rawJSON = convertStreamToString(is);
    return rawJSON.length() > 0 ? new JSONObject(rawJSON) : new JSONObject();
  }

  public void shutdown() {
    if (_httpclient == null) return;
    _httpclient.getConnectionManager().shutdown();
    _httpclient = null;
  }
}
