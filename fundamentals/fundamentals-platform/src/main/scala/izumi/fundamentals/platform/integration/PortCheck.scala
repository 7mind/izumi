package izumi.fundamentals.platform.integration

import java.net.{InetAddress, InetSocketAddress, Socket, URI, URL}
import scala.concurrent.duration._

/**
  * This class is intended to be always present in the DI object graph and injected
  * into each resource which needs a port availability check.
  *
  * The timeout is intended to be defined just once per app.
  *
  * @param timeout port check timeout
  */
class PortCheck(timeout: FiniteDuration) {

  @deprecated(
    "PortCheck constructor now accepts FiniteDuration instead of Int of milliseconds. Rewrite from `new PortCheck(100)` to `new PortCheck(100.millis)`",
    "Int constructor will be removed in 0.11.1",
  )
  def this(timeoutMillis: Int) = this(timeoutMillis.millis)

  def checkUrl(uri: URL, clue: String, defaultPort: Int): ResourceCheck = {
    checkUrl(uri, Some(clue), Some(defaultPort))
  }

  def checkUrl(uri: URL, clue: String): ResourceCheck = {
    checkUrl(uri, Some(clue), None)
  }

  def checkUri(uri: URI, defaultPort: Int, clue: String): ResourceCheck = {
    checkUri(uri, defaultPort, Some(clue))
  }

  def checkPort(host: String, port: Int, clue: String): ResourceCheck = {
    checkPort(host, port, Some(clue))
  }

  def checkAddressPort(address: InetAddress, port: Int, clue: String): ResourceCheck = {
    checkAddress(new InetSocketAddress(address, port), Some(clue))
  }

  def checkUrl(uri: URL, clue: Option[String] = None, defaultPort: Option[Int] = None): ResourceCheck = {
    val portOrDefault: Int = portFor(uri.getDefaultPort, defaultPort, uri.getPort)
    checkPort(uri.getHost, portOrDefault, clue)
  }

  def checkUri(uri: URI, defaultPort: Int, clue: Option[String] = None): ResourceCheck = {
    val portOrDefault: Int = portFor(defaultPort, Some(defaultPort), uri.getPort)
    checkPort(uri.getHost, portOrDefault, clue)
  }

  def checkPort(host: String, port: Int, clue: Option[String] = None): ResourceCheck = {
    checkAddress(new InetSocketAddress(host, port), clue)
  }

  def checkAddressPort(address: InetAddress, port: Int, clue: Option[String] = None): ResourceCheck = {
    checkAddress(new InetSocketAddress(address, port), clue)
  }

  def checkAddress(address: InetSocketAddress, clue: Option[String] = None): ResourceCheck = {
    try {
      val socket = new Socket()
      try {
        socket.connect(address, timeout.toMillis.toInt)
        ResourceCheck.Success()
      } finally {
        socket.close()
      }
    } catch {
      case t: Throwable =>
        val message = clue match {
          case Some(_) =>
            s"$clue: port check failed on ${address.getHostName}:${address.getPort}, timeout: $timeout"
          case None =>
            s"Port check failed on ${address.getHostName}:${address.getPort}, timeout: $timeout"
        }

        ResourceCheck.ResourceUnavailable(message, Some(t))
    }
  }

  private def portFor(uriPort: => Int, defaultPort: Option[Int], port: Int) = {
    val portOrDefault = port match {
      case -1 =>
        defaultPort.getOrElse(uriPort)
      case v =>
        v
    }
    portOrDefault
  }

}
