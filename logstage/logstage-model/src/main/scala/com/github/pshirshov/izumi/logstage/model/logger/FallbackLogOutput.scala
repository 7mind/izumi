package com.github.pshirshov.izumi.logstage.model.logger

trait FallbackLogOutput {
  def flush(s: String)
  def flush(s: String, e: Throwable)
}

object FallbackLogOutput extends FallbackLogOutput {
  override def flush(s: String): Unit = {
    System.err.println(s)
  }

  override def flush(s: String, e: Throwable): Unit = {
    System.err.println(s"$s\n${e.stackTrace}")
  }
}
