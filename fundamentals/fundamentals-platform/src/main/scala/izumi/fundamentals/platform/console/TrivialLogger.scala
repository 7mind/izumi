package com.github.pshirshov.izumi.fundamentals.platform.console

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import scala.reflect.ClassTag

trait TrivialLogger {
  def log(s: => String): Unit

  def log(s: => String, e: => Throwable): Unit

  def sub(): TrivialLogger =  sub(1)
  def sub(delta: Int): TrivialLogger
}

class TrivialLoggerImpl(sink: AbstractStringTrivialSink, level: Int) extends TrivialLogger {
  override def log(s: => String): Unit = {
    sink.flush(s.shift(level * 2))
  }

  override def log(s: => String, e: => Throwable): Unit = {
    import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable._
    log(s"$s\n${e.stackTrace}")
  }

  override def sub(delta: Int): TrivialLogger =  new TrivialLoggerImpl(sink, level + delta)
}

class TrivialLoggerNullImpl() extends TrivialLogger {
  override def log(s: => String): Unit = {
    s.forget
  }

  override def log(s: => String, e: => Throwable): Unit = {
    (s, e).forget
  }

  override def sub(): TrivialLogger = this

  override def sub(delta: Int): TrivialLogger = this
}

object TrivialLogger {
  def make[T: ClassTag](id: String, sink: AbstractStringTrivialSink = SystemErrStringTrivialSink, forceLog: Boolean = false, default: Boolean = false): TrivialLogger = {
    import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

    val sink0 = if (System.getProperty(id).asBoolean().getOrElse(default) || forceLog) {
      sink
    } else {
      NullStringTrivialSink
    }

    new TrivialLoggerImpl(sink0, 0)
  }


  def makeOut[T: ClassTag](id: String, forceLog: Boolean = false, default: Boolean = false): TrivialLogger = {
    make[T](id, SystemOutStringTrivialSink, forceLog, default)
  }
  def makeErr[T: ClassTag](id: String, forceLog: Boolean = false, default: Boolean = false): TrivialLogger = {
    make[T](id, SystemErrStringTrivialSink, forceLog, default)
  }
}

