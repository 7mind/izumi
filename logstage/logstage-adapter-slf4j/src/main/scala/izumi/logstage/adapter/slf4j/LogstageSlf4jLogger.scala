package izumi.logstage.adapter.slf4j

import izumi.fundamentals.platform.language.SourceFilePosition
import izumi.logstage.api.Log._
import izumi.logstage.api.logger.LogRouter
import org.slf4j.{Logger, Marker}

import scala.collection.compat.immutable.ArraySeq

class LogstageSlf4jLogger(name: String, router: LogRouter) extends Logger {
  private val id = LoggerId(name)

  override def getName: String = name

  private def log(level: Level, message: String, args: => Seq[Any]): Unit = {
    if (router.acceptable(id, level)) {
      router.log(mkEntry(level, message, args, None))
    }
  }

  private def log(level: Level, message: String, args: => Seq[Any], marker: => Marker): Unit = {
    if (router.acceptable(id, level)) {
      router.log(mkEntry(level, message, args, Option(marker)))
    }
  }

  @inline private[this] def mkEntry(level: Level, message: String, args: => Seq[Any], marker: => Option[Marker]): Entry = {
    val thread = Thread.currentThread()
    val threadData = ThreadData(thread.getName, thread.getId)

    val caller = thread.getStackTrace.tail.find(_.getClassName != getClass.getCanonicalName)

    val ctx = caller match {
      case Some(frame) =>
        StaticExtendedContext(id, SourceFilePosition(frame.getFileName, frame.getLineNumber))

      case None =>
        StaticExtendedContext(id, SourceFilePosition.unknown)
    }

    val customContext = marker match {
      case Some(m) =>
        import scala.jdk.CollectionConverters._
        val markers = m.iterator().asScala.toSeq.map(_.getName)
        CustomContext(Seq(LogArg(Seq("markers"), markers, hiddenName = false, None)))

      case None =>
        CustomContext(Seq.empty)
    }

    val messageArgs = args.zipWithIndex.map {
      kv =>
        LogArg(Seq(s"_${kv._2}"), kv._1, hiddenName = true, None)
    }

    val template = message.split("\\{\\}", -1).map(_.replace("\\", "\\\\"))

    Entry(
      Message(StringContext(ArraySeq.unsafeWrapArray(template): _*), messageArgs),
      Context(
        ctx,
        DynamicContext(level, threadData, System.currentTimeMillis()),
        customContext,
      ),
    )
  }

  override def isTraceEnabled: Boolean = router.acceptable(id, Level.Trace)

  override def isInfoEnabled: Boolean = router.acceptable(id, Level.Info)

  override def isDebugEnabled: Boolean = router.acceptable(id, Level.Debug)

  override def isWarnEnabled: Boolean = router.acceptable(id, Level.Warn)

  override def isErrorEnabled: Boolean = router.acceptable(id, Level.Error)

  override def isTraceEnabled(marker: Marker): Boolean = isTraceEnabled

  override def isDebugEnabled(marker: Marker): Boolean = isDebugEnabled

  override def isInfoEnabled(marker: Marker): Boolean = isInfoEnabled

  override def isWarnEnabled(marker: Marker): Boolean = isWarnEnabled

  override def isErrorEnabled(marker: Marker): Boolean = isErrorEnabled

  override def trace(msg: String): Unit = log(Level.Trace, msg, Seq.empty)

  override def trace(format: String, arg: Any): Unit = log(Level.Trace, format, Seq(arg))

  override def trace(format: String, arg1: Any, arg2: Any): Unit = log(Level.Trace, format, Seq(arg1, arg2))

  override def trace(format: String, arguments: AnyRef*): Unit = log(Level.Trace, format, arguments)

  override def trace(msg: String, t: Throwable): Unit = log(Level.Trace, msg, Option(t).toSeq)

  override def trace(marker: Marker, msg: String): Unit = log(Level.Trace, msg, Seq.empty, marker)

  override def trace(marker: Marker, format: String, arg: Any): Unit = log(Level.Trace, format, Seq(arg), marker)

  override def trace(marker: Marker, format: String, arg1: Any, arg2: Any): Unit = log(Level.Trace, format, Seq(arg1, arg2), marker)

  override def trace(marker: Marker, format: String, argArray: AnyRef*): Unit = log(Level.Trace, format, argArray, marker)

  override def trace(marker: Marker, msg: String, t: Throwable): Unit = log(Level.Trace, msg, Option(t).toSeq, marker)

  override def debug(msg: String): Unit = log(Level.Debug, msg, Seq.empty)

  override def debug(format: String, arg: Any): Unit = log(Level.Debug, format, Seq(arg))

  override def debug(format: String, arg1: Any, arg2: Any): Unit = log(Level.Debug, format, Seq(arg1, arg2))

  override def debug(format: String, arguments: AnyRef*): Unit = log(Level.Debug, format, arguments)

  override def debug(msg: String, t: Throwable): Unit = log(Level.Debug, msg, Option(t).toSeq)

  override def debug(marker: Marker, msg: String): Unit = log(Level.Debug, msg, Seq.empty, marker)

  override def debug(marker: Marker, format: String, arg: Any): Unit = log(Level.Debug, format, Seq(arg), marker)

  override def debug(marker: Marker, format: String, arg1: Any, arg2: Any): Unit = log(Level.Debug, format, Seq(arg1, arg2), marker)

  override def debug(marker: Marker, format: String, argArray: AnyRef*): Unit = log(Level.Debug, format, argArray, marker)

  override def debug(marker: Marker, msg: String, t: Throwable): Unit = log(Level.Debug, msg, Option(t).toSeq, marker)

  override def info(msg: String): Unit = log(Level.Info, msg, Seq.empty)

  override def info(format: String, arg: Any): Unit = log(Level.Info, format, Seq(arg))

  override def info(format: String, arg1: Any, arg2: Any): Unit = log(Level.Info, format, Seq(arg1, arg2))

  override def info(format: String, arguments: AnyRef*): Unit = log(Level.Info, format, arguments)

  override def info(msg: String, t: Throwable): Unit = log(Level.Info, msg, Option(t).toSeq)

  override def info(marker: Marker, msg: String): Unit = log(Level.Info, msg, Seq.empty, marker)

  override def info(marker: Marker, format: String, arg: Any): Unit = log(Level.Info, format, Seq(arg), marker)

  override def info(marker: Marker, format: String, arg1: Any, arg2: Any): Unit = log(Level.Info, format, Seq(arg1, arg2), marker)

  override def info(marker: Marker, format: String, argArray: AnyRef*): Unit = log(Level.Info, format, argArray, marker)

  override def info(marker: Marker, msg: String, t: Throwable): Unit = log(Level.Info, msg, Option(t).toSeq, marker)

  override def warn(msg: String): Unit = log(Level.Warn, msg, Seq.empty)

  override def warn(format: String, arg: Any): Unit = log(Level.Warn, format, Seq(arg))

  override def warn(format: String, arg1: Any, arg2: Any): Unit = log(Level.Warn, format, Seq(arg1, arg2))

  override def warn(format: String, arguments: AnyRef*): Unit = log(Level.Warn, format, arguments)

  override def warn(msg: String, t: Throwable): Unit = log(Level.Warn, msg, Option(t).toSeq)

  override def warn(marker: Marker, msg: String): Unit = log(Level.Warn, msg, Seq.empty, marker)

  override def warn(marker: Marker, format: String, arg: Any): Unit = log(Level.Warn, format, Seq(arg), marker)

  override def warn(marker: Marker, format: String, arg1: Any, arg2: Any): Unit = log(Level.Warn, format, Seq(arg1, arg2), marker)

  override def warn(marker: Marker, format: String, argArray: AnyRef*): Unit = log(Level.Warn, format, argArray, marker)

  override def warn(marker: Marker, msg: String, t: Throwable): Unit = log(Level.Warn, msg, Option(t).toSeq, marker)

  override def error(msg: String): Unit = log(Level.Error, msg, Seq.empty)

  override def error(format: String, arg: Any): Unit = log(Level.Error, format, Seq(arg))

  override def error(format: String, arg1: Any, arg2: Any): Unit = log(Level.Error, format, Seq(arg1, arg2))

  override def error(format: String, arguments: AnyRef*): Unit = log(Level.Error, format, arguments)

  override def error(msg: String, t: Throwable): Unit = log(Level.Error, msg, Option(t).toSeq)

  override def error(marker: Marker, msg: String): Unit = log(Level.Error, msg, Seq.empty, marker)

  override def error(marker: Marker, format: String, arg: Any): Unit = log(Level.Error, format, Seq(arg), marker)

  override def error(marker: Marker, format: String, arg1: Any, arg2: Any): Unit = log(Level.Error, format, Seq(arg1, arg2), marker)

  override def error(marker: Marker, format: String, argArray: AnyRef*): Unit = log(Level.Error, format, argArray, marker)

  override def error(marker: Marker, msg: String, t: Throwable): Unit = log(Level.Error, msg, Option(t).toSeq, marker)
}
