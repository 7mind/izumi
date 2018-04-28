package com.github.pshirshov.izumi.fundamentals.platform.console

import scala.reflect.ClassTag

trait TrivialLogger {
  def log(s: => String): Unit

  def log(s: String, e: Throwable): Unit
}

class TrivialLoggerImpl(sink: AbstractStringSink) extends TrivialLogger {
  override def log(s: => String): Unit = sink.flush(s)

  override def log(s: String, e: Throwable): Unit = {
    import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable._
    sink.flush(s"$s\n${e.stackTrace}")
  }
}

class TrivialLoggerNullImpl() extends TrivialLogger {
  override def log(s: => String): Unit = ???

  override def log(s: String, e: Throwable): Unit = ???
}

object TrivialLogger {
  def make[T: ClassTag](id: String, forceLog: Boolean = false): TrivialLogger = {
    import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

    val sink = if (System.getProperty(id).asBoolean().getOrElse(false) || forceLog) {
      SystemErrStringSink
    } else {
      NullStringSink
    }

    new TrivialLoggerImpl(sink)
  }

}

