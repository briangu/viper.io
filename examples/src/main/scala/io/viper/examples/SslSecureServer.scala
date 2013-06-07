package io.viper.examples

import io.viper.common._
import java.io.File

// curl -k https://localhost:8080/hello

object SslSecureServerExample {

  def main(args: Array[String]) {
    val password = "password"
    val keyStorePath = "viper_keystore.jks"

    if (!new File(keyStorePath).exists) {
      FileKeyStoreManager.generateSelfSignedKeyStore(keyStorePath, password, password, "viper", "viper")
    }

    val keyStore = new FileKeyStoreManager(keyStorePath, password, password)
    val server = new SslSecureServer("signing.com", "res:///signin/", keyStore)
    NestServer.run(8080, server)
  }
}

class SslSecureServer(hostname: String, resource: String, keyStoreManager: KeyStoreManager) extends SSLServer(hostname, resource, keyStoreManager) {
  get("/hello", { args => Response("world") })
}
