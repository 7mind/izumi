package com.github.pshirshov.izumi.logstage.config.exceptions

abstract class LogstageException(message: String, cause: Option[Throwable]) extends RuntimeException(message) {
  cause.foreach(this.initCause)
}
