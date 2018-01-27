package com.github.pshirshov.izumi.logstage.model


trait AbstractLogger {
  def debug(message: String): Unit
}
