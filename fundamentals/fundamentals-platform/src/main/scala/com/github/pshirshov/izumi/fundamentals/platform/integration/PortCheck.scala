package com.github.pshirshov.izumi.fundamentals.platform.integration

import java.net.{InetSocketAddress, Socket, URI, URL}

class PortCheck(timeout: Int) {
  def checkUrl(uri: URL, clue: String, defaultPort: Int): ResourceCheck = {
    checkUrl(uri, Some(clue), Some(defaultPort))
  }

  def checkUrl(uri: URL, clue: String): ResourceCheck = {
    checkUrl(uri, Some(clue), None)
  }

  def checkUri(uri: URI, clue: String): ResourceCheck = {
    checkUri(uri, Some(clue))
  }

  def checkPort(host: String, port: Int, clue: String): ResourceCheck = {
    checkPort(host, port, Some(clue))
  }

  def checkUrl(uri: URL, clue: Option[String] = None, defaultPort: Option[Int] = None): ResourceCheck = {
    val port = uri.getPort match {
      case -1 =>
        defaultPort.getOrElse(uri.getDefaultPort)
      case v =>
        v
    }
    checkPort(uri.getHost, port, clue)
  }

  def checkUri(uri: URI, clue: Option[String] = None): ResourceCheck = {
    checkPort(uri.getHost, uri.getPort, clue)
  }

  def checkPort(host: String, port: Int, clue: Option[String] = None): ResourceCheck = {
    try {
      val socket = new Socket()
      try {
        socket.connect(new InetSocketAddress(host, port), timeout)
        ResourceCheck.Success()
      } finally {
        socket.close()
      }
    } catch {
      case t: Throwable =>
        val message = clue match {
          case Some(_) =>
            s"$clue: port check failed on $host:$port, timeout: $timeout ms"
          case None =>
            s"Port check failed on $host:$port, timeout: $timeout ms"
        }

        ResourceCheck.ResourceUnavailable(message, Some(t))
    }
  }

}
