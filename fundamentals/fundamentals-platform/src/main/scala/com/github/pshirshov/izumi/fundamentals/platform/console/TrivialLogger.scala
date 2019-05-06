package com.github.pshirshov.izumi.fundamentals.platform.console

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._

import scala.reflect.ClassTag

trait TrivialLogger {
  def log(s: => String): Unit

  def log(s: String, e: Throwable): Unit
}

class TrivialLoggerImpl(sink: AbstractStringTrivialSink) extends TrivialLogger {
  override def log(s: => String): Unit = {
    sink.flush(s)
  }

  override def log(s: String, e: Throwable): Unit = {
    import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable._
    sink.flush(s"$s\n${e.stackTrace}")
  }
}

class TrivialLoggerNullImpl() extends TrivialLogger {
  override def log(s: => String): Unit = {
    s.forget
  }

  override def log(s: String, e: Throwable): Unit = {
    (s, e).forget
  }
}

object TrivialLogger {
  def make[T: ClassTag](id: String, sink: AbstractStringTrivialSink = SystemErrStringTrivialSink, forceLog: Boolean = false, default: Boolean = false): TrivialLogger = {
    import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

    val sink0 = if (System.getProperty(id).asBoolean().getOrElse(default) || forceLog) {
      sink
    } else {
      NullStringTrivialSink
    }

    new TrivialLoggerImpl(sink0)
  }

}

