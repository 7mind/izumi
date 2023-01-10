package izumi.fundamentals.platform.console

import izumi.fundamentals.platform.console.TrivialLogger.{Config, Level}
import izumi.fundamentals.platform.exceptions.IzThrowable._
import izumi.fundamentals.platform.strings.IzString._

import scala.annotation.nowarn
import scala.collection.mutable
import scala.reflect.{ClassTag, classTag}

trait TrivialLogger {
  def log(s: => String): Unit
  def log(s: => String, e: => Throwable): Unit

  def err(s: => String): Unit
  def err(s: => String, e: => Throwable): Unit

  def sub(): TrivialLogger = sub(1)
  def sub(delta: Int): TrivialLogger
}

trait AbstractStringTrivialSink {
  def flush(value: => String): Unit
  def flushError(value: => String): Unit
}

object AbstractStringTrivialSink {
  object Console extends AbstractStringTrivialSink {
    override def flush(value: => String): Unit = System.out.println(value)
    override def flushError(value: => String): Unit = System.err.println(value)
  }
}

final class TrivialLoggerImpl(
  config: Config,
  id: String,
  logMessages: Boolean,
  logErrors: Boolean,
  loggerLevel: Int,
) extends TrivialLogger {
  override def log(s: => String): Unit = {
    flush(Level.Info, format(s))
  }

  override def log(s: => String, e: => Throwable): Unit = {
    flush(Level.Info, formatError(s, e))
  }

  override def err(s: => String): Unit = {
    flush(Level.Error, format(s))
  }

  override def err(s: => String, e: => Throwable): Unit = {
    flush(Level.Error, formatError(s, e))
  }

  override def sub(delta: Int): TrivialLogger = {
    new TrivialLoggerImpl(config, id, logMessages, logErrors, loggerLevel + delta)
  }

  @inline private[this] def format(s: => String): String = {
    s"$id: $s"
  }

  @inline private[this] def formatError(s: => String, e: => Throwable): String = {
    s"$id: $s\n${e.stackTrace}"
  }

  @inline private[this] def flush(level: Level, s: => String): Unit = {
    level match {
      case Level.Info =>
        if (logMessages) {
          config.sink.flush(s.shift(loggerLevel * 2))
        }
      case Level.Error =>
        if (logErrors) {
          config.sink.flushError(s.shift(loggerLevel * 2))
        }
    }
  }
}

object TrivialLogger {
  sealed trait Level
  object Level {
    case object Info extends Level
    case object Error extends Level
  }

  final case class Config(
    sink: AbstractStringTrivialSink = AbstractStringTrivialSink.Console,
    forceLog: Boolean = false,
  )

  def make[T: ClassTag](sysProperty: String, config: Config = Config()): TrivialLogger = {
    val logMessages: Boolean = checkLog(sysProperty, config, default = false)
    val logErrors: Boolean = checkLog(sysProperty, config, default = true)
    new TrivialLoggerImpl(config, classTag[T].runtimeClass.getSimpleName, logMessages, logErrors, loggerLevel = 0)
  }

  private[this] val enabled = new mutable.HashMap[String, Boolean]()

  @nowarn("msg=return statement uses an exception")
  private[this] def checkLog(sysProperty: String, config: Config, default: Boolean): Boolean = enabled.synchronized {
    def check(): Boolean = {
      val parts = sysProperty.split('.')

      def cond(path: String): Boolean = {
        System.getProperty(path).asBoolean().getOrElse(default)
      }

      var idx = 0
      var out = false
      var current = parts.head

      while (idx < parts.tail.length) {
        out = cond(current)
        if (out) {
          idx = Int.MaxValue
        } else {
          val p = parts.tail(idx)
          current = s"$current.$p"
          idx = idx + 1

        }
      }

      out
    }

    config.forceLog || enabled.getOrElseUpdate(sysProperty, check())
  }
}
