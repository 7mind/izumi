package izumi.logstage.adapter.jul
import izumi.fundamentals.platform.language.SourceFilePosition
import izumi.logstage.api.Log
import logstage.LogRouter

import java.util.logging.{LogManager, LogRecord}
import scala.collection.compat.immutable.ArraySeq

/**
  * If you don't like our JUL adapter, you still might use our SLF4J adapter with `jul-to-slf4j`
  *
  * Don't forget do do something like
  * {{{
  * SLF4JBridgeHandler.removeHandlersForRootLogger()
  * SLF4JBridgeHandler.install()
  * }}}
  */
class LogstageJulLogger(router: LogRouter) extends java.util.logging.Handler {
  override def publish(record: LogRecord): Unit = {
    val level = toLevel(record)
    if (router.acceptable(Log.LoggerId(record.getLoggerName), level)) {
      router.log(mkEntry(record))
    }
  }

  def toLevel(record: LogRecord): Log.Level = {
    val level = record.getLevel.intValue()
    import java.util.logging.Level._
    if (level >= SEVERE.intValue()) {
      Log.Level.Crit
    } else if (level >= WARNING.intValue()) {
      Log.Level.Warn
    } else if (level >= INFO.intValue()) {
      Log.Level.Info
    } else if (level >= FINE.intValue()) {
      Log.Level.Debug
    } else {
      Log.Level.Trace
    }
  }

  @inline private[this] def mkEntry(record: LogRecord): Log.Entry = {

    val id = Log.LoggerId(record.getLoggerName)

    val thread = Thread.currentThread()

    val ctx = Log.StaticExtendedContext(id, SourceFilePosition.unknown)
    val threadData = Log.ThreadData(thread.getName, thread.getId)

    val allParams = if (record.getThrown == null) {
      record.getParameters.toSeq
    } else {
      record.getParameters.toSeq ++ Seq(record.getThrown)
    }

    val messageArgs = allParams.zipWithIndex.map {
      kv =>
        Log.LogArg(Seq(s"_${kv._2}"), kv._1, hiddenName = true, None)
    }

    val template = record.getMessage.split("\\{\\d+\\}", -1).map(_.replace("\\", "\\\\"))

    val level = toLevel(record)
    Log.Entry(
      Log.Message(StringContext(ArraySeq.unsafeWrapArray(template): _*), messageArgs),
      Log.Context(
        ctx,
        Log.DynamicContext(level, threadData, System.currentTimeMillis()),
        Log.CustomContext(
          ("source", "jul"),
          ("class", record.getSourceClassName),
          ("method", record.getSourceMethodName),
          ("time", record.getMillis),
        ),
      ),
    )
  }

  override def flush(): Unit = {}

  override def close(): Unit = {
    uninstall()
  }

  def install(): Unit = {
    LogManager.getLogManager.getLogger("").addHandler(this)
  }

  def installOnly(): Unit = {
    val rootLogger = LogManager.getLogManager.getLogger("")
    rootLogger.synchronized {
      val handlers = rootLogger.getHandlers
      for (handler <- handlers) {
        rootLogger.removeHandler(handler)
      }
    }
    install()
  }

  def uninstall(): Unit = {
    val rootLogger = LogManager.getLogManager.getLogger("")
    rootLogger.synchronized {
      val handlers = rootLogger.getHandlers
      for (handler <- handlers) {
        if (handler.isInstanceOf[LogstageJulLogger]) rootLogger.removeHandler(handler)
      }
    }
  }
}
