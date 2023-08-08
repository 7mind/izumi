package izumi.logstage.adapter.jul

import izumi.fundamentals.platform.language.SourceFilePosition
import izumi.logstage.api.Log
import logstage.LogRouter

import java.util.logging.{Handler, LogManager, LogRecord, Logger}
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
class LogstageJulLogger(router: LogRouter) extends java.util.logging.Handler with JULTools with AutoCloseable {
  override def publish(record: LogRecord): Unit = {
    val level = toLevel(record)
    if (router.acceptable(Log.LoggerId(record.getLoggerName), level)) {
      router.log(mkEntry(record))
    }
  }

  def toLevel(record: LogRecord): Log.Level = {
    val level = record.getLevel.intValue()
    import java.util.logging.Level.*
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

    val loggerName = if (record.getLoggerName == null) "unknown" else record.getLoggerName
    val id = Log.LoggerId(loggerName)

    val thread = Thread.currentThread()

    val ctx = Log.StaticExtendedContext(id, SourceFilePosition.unknown)
    val threadData = Log.ThreadData(thread.getName, thread.getId)

    val params = if (record.getParameters == null) Seq.empty else record.getParameters.toSeq
    val allParams = if (record.getThrown == null) params else params ++ Seq(record.getThrown)

    val messageArgs = allParams.zipWithIndex.map {
      kv =>
        Log.LogArg(Seq(s"_${kv._2}"), kv._1, hiddenName = true, None)
    }

    val template = if (record.getMessage == null) {
      Array.empty
    } else {
      record.getMessage.split("\\{\\d+\\}", -1).map(_.replace("\\", "\\\\"))
    }

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

  override protected def handler(): Handler = this
}

trait JULTools {

  protected def handler(): Handler

  def install(): Unit = {
    install(rootRef())
  }

  def install(logger: Logger): Unit = {
    logger.addHandler(handler())
  }

  def installOnly(): Unit = {
    installOnly(rootRef())
  }

  def installOnly(logger: Logger): Unit = {
    uninstallAll(logger)
    install(logger)
  }

  def uninstallAll(): Unit = {
    uninstallAll(rootRef())
  }

  def uninstallAll(logger: Logger): Unit = {
    forEachHandler(logger) {
      case (l, handler) =>
        l.removeHandler(handler)
    }
  }

  def uninstall(): Unit = {
    uninstall(rootRef())
  }

  def uninstall(logger: Logger): Unit = {
    forEachHandler(logger) {
      case (l, handler) =>
        if (handler.isInstanceOf[LogstageJulLogger]) l.removeHandler(handler)
    }
  }

  def rootRef(): Logger = LogManager.getLogManager.getLogger("")

  private[this] def forEachHandler(logger: Logger)(action: (Logger, java.util.logging.Handler) => Unit): Unit = {
    logger.synchronized {
      val handlers = logger.getHandlers
      for (handler <- handlers) {
        action(logger, handler)
      }
    }
  }
}
