package izumi.fundamentals.platform.integration

import izumi.fundamentals.platform.integration.PortCheck.HostPortPair

import java.net.*
import scala.concurrent.duration.*

/**
  * This class is intended to be always present in the DI object graph and injected
  * into each resource which needs a port availability check.
  *
  * The timeout is intended to be defined just once per app.
  *
  * @param timeout port check timeout
  */
class PortCheck(timeout: FiniteDuration) {

  def checkUrl(url: URL, clue: String, defaultPort: Int): ResourceCheck = {
    checkUrl(url, Some(clue), Some(defaultPort))
  }

  def checkUrl(url: URL, clue: String): ResourceCheck = {
    checkUrl(url, Some(clue), None)
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

  def checkUrl(url: URL, clue: Option[String] = None, defaultPort: Option[Int] = None): ResourceCheck = {
    val portOrDefault: Int = portFor(url.getDefaultPort, defaultPort, url.getPort)
    checkPort(url.getHost, portOrDefault, clue)
  }

  def checkUri(uri: URI, defaultPort: Int, clue: Option[String] = None): ResourceCheck = {
    val portOrDefault: Int = portFor(defaultPort, Some(defaultPort), uri.getPort)
    checkPort(uri.getHost, portOrDefault, clue)
  }

  def checkPort(host: String, port: Int, clue: Option[String] = None): ResourceCheck = {
    checkAddress(new InetSocketAddress(host, port), clue)
  }

  def check(port: HostPortPair, clue: Option[String] = None): ResourceCheck = {
    checkAddressPort(port.addr, port.port, clue)
  }

  def checkAddressPort(address: InetAddress, port: Int, clue: Option[String] = None): ResourceCheck = {
    checkAddress(new InetSocketAddress(address, port), clue)
  }

  def checkAddress(address: => InetSocketAddress, clue: Option[String] = None): ResourceCheck = {
    try {
      val evaluatedAddress = address
      try {
        val socket = new Socket
        try {
          socket.connect(evaluatedAddress, timeout.toMillis.toInt)
          ResourceCheck.Success()
        } finally {
          socket.close()
        }
      } catch {
        case t: Throwable =>
          val message =
            errorMessage(s"${evaluatedAddress.getAddress.getHostAddress}==${evaluatedAddress.getHostName}:${evaluatedAddress.getPort}, timeout: $timeout", clue)
          ResourceCheck.ResourceUnavailable(message, Some(t))
      }
    } catch {
      case t: Throwable =>
        val message = errorMessage(s"unknown address, timeout: $timeout", clue)
        ResourceCheck.ResourceUnavailable(message, Some(t))
    }
  }

  private def errorMessage(ctx: String, mbClue: Option[String]): String = {
    mbClue match {
      case Some(clue) =>
        s"Port check of $clue failed on $ctx"
      case None =>
        s"Port check failed on $ctx"
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

object PortCheck {
  trait HostPortPair {
    def addr: InetAddress
    def port: Int
  }
}
