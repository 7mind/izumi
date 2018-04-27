package com.github.pshirshov.izumi.logstage.model.logger

trait FallbackLogOutput {
  def flush(s: String): Unit
  def flush(s: String, e: Throwable): Unit
}

object FallbackLogOutput extends FallbackLogOutput {
  override def flush(s: String): Unit = {
    System.err.println(s)
  }

  override def flush(s: String, e: Throwable): Unit = {
    import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable._
    System.err.println(s"$s\n${e.stackTrace}")
  }
}
