package io.viper.common


import io.viper.core.server.file.StaticFileServerHandler
import org.jboss.netty.channel.{ChannelPipeline, ChannelPipelineFactory}
import java.util
import io.viper.core.server.router._
import collection.mutable.ListBuffer
import org.jboss.netty.handler.ssl.SslHandler

import java.security.KeyStore
import java.security.Security

import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import java.io.{File, FileInputStream, InputStream}

class ViperServer(resourcePath: String) extends ChannelPipelineFactory with RestServer
{
  var resourceInstance: Class[_]  = this.getClass

  override def getPipeline: ChannelPipeline = {
    routes = List[Route]()
    addRoutes()
    addDefaultRoutes()
    buildPipeline()
  }

  override def addRoutes() {}

  private def addDefaultRoutes() {
    val provider = StaticFileContentInfoProviderFactory.create(resourceInstance, resourcePath)
    val handler = new StaticFileServerHandler(provider)
    get("/$path", handler)
    get("/", handler)
  }
}

object VirtualServer {
  def apply(hostname: String): VirtualServer = new VirtualServer(hostname)
  def apply(hostname: String, resourcePath: String) = new VirtualServer(hostname, resourcePath)
}

class VirtualServer(val hostname: String, resourcePath: String) extends ViperServer(resourcePath) with VirtualServerRunner {
  def this(hostname: String) {
    this(hostname, "res:///%s/".format(hostname))
  }

  private val initCode = new ListBuffer[() => Unit]

  override def addRoutes() {
    for (proc <- initCode) proc()
  }

  def get(route: String, f:(util.Map[String, String]) => RouteResponse): VirtualServer = {
    initCode.append(() => { super.get(route)(f) })
    this
  }
  def put(route: String, f:(util.Map[String, String]) => RouteResponse): VirtualServer = {
    initCode.append(() => { super.put(route)(f) })
    this
  }
  def post(route: String, f:(util.Map[String, String]) => RouteResponse): VirtualServer = {
    initCode.append(() => { super.post(route)(f) })
    this
  }
  def delete(route: String, f:(util.Map[String, String]) => RouteResponse): VirtualServer = {
    initCode.append(() => { super.delete(route)(f) })
    this
  }

  def main(args: Array[String]) {
    val port = if (args.length > 0) args(0).toInt else 8080
    val hostRouterHandler = new HostRouterHandler
    if (port != 80) StaticFileContentInfoProviderFactory.enableCache(enabled = false)
    hostRouterHandler.putRoute("localhost", port, create)
    NestServer.create(port, hostRouterHandler)
    Thread.currentThread.join()
  }

  def start() {}
  def stop() {}
  def create: ViperServer = this
}

trait VirtualServerRunner {
  def hostname: String
  def start()
  def stop()
  def create: ViperServer
}

class SSLServer(hostname: String, resourcePath: String, keysStoreManager: KeyStoreManager) extends VirtualServer(hostname, resourcePath)
{
  def this(hostname: String, keysStoreManager: KeyStoreManager) {
    this(hostname, "res:///%s/".format(hostname), keysStoreManager)
  }

  override def getPipeline: ChannelPipeline = {
    val pipeline = super.getPipeline

    SslContext.create(keysStoreManager) match {
      case Some(serverContext) => {
        val sslEngine = serverContext.createSSLEngine()
        sslEngine.setUseClientMode(false)
        pipeline.addFirst("ssl", new SslHandler(sslEngine))
      }
      case None => {
        throw new RuntimeException("could not create ssl engine")
      }
    }

    pipeline
  }
}

trait KeyStoreManager {

  def getKeyStoreAsInputStream: InputStream
  def getKeyStorePassword: Array[Char]
  def getKeyStoreAlgorithm: String = {
    var algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm")
    if (algorithm == null) {
      algorithm = "SunX509"
    }
    algorithm
  }
  def getCertificatePassword: Array[Char]

}

object FileKeyStoreManager
{
  def generateSelfSignedKeyStore(path: String, keyPass: String, storePass: String, alias: String, commonName: String) {
    nativeCall("keytool",
               "-genkey",
               "-alias", alias,
               "-keysize", "4096",
               "-validity", "36500",
               "-keyalg", "RSA",
               "-dname", "CN=%s".format(commonName),
               "-keypass", keyPass,
               "-storepass", storePass,
               "-keystore", path)

    nativeCall("keytool",
               "-exportcert",
               "-alias", alias,
               "-keystore", path,
               "-storepass", storePass,
               "-file", "%s.cert".format(path))
  }

  private def nativeCall(commands: String*): String = {
    import scala.collection.JavaConverters._

    //log.info("Running '{}'", Arrays.asList(commands))
    val pb = new ProcessBuilder(commands.toList.asJava)
    try {
      val process = pb.start()
      val is = process.getInputStream
      val data = scala.io.Source.fromInputStream(is).mkString
      //log.info("Completed native call: '{}'\nResponse: '"+data+"'", Arrays.asList(commands))
      data
    } catch {
      case e: Exception => {
        //log.error("Error running commands: " + Arrays.asList(commands), e)
        ""
      }
    }
  }
}

class FileKeyStoreManager(path: String, password: String, certPassword: String) extends KeyStoreManager {

  private val _passwordChars = password.toCharArray
  private val _certPasswordChars = certPassword.toCharArray

  def getKeyStoreAsInputStream: InputStream = {
    val file = new File(path)
    if (file.exists()) {
      new FileInputStream(file)
    } else {
      throw new IllegalArgumentException("key store file does not exist at path: %s".format(path))
    }
  }

  def getKeyStorePassword: Array[Char] = {
    _passwordChars
  }

  def getCertificatePassword: Array[Char] = {
    _certPasswordChars
  }
}

object SslContext {

  private val PROTOCOL = "TLS"

  def create(ksm: KeyStoreManager): Option[SSLContext] = {
    create(ksm, null)
  }

  def create(ksm: KeyStoreManager, trustManagers: Array[TrustManager]): Option[SSLContext] = {

    try {
      val ks = KeyStore.getInstance("JKS")
      ks.load(ksm.getKeyStoreAsInputStream, ksm.getKeyStorePassword)

      // Set up key manager factory to use our key store
      val kmf = KeyManagerFactory.getInstance(ksm.getKeyStoreAlgorithm)
      kmf.init(ks, ksm.getCertificatePassword)

      // Initialize the SSLContext to work with our key managers.
      val serverContext = SSLContext.getInstance(PROTOCOL)
      serverContext.init(kmf.getKeyManagers, trustManagers, null)
      Some(serverContext)
    } catch {
      case e: Exception => {
        None
      }
    }
  }
}
