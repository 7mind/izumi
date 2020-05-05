package izumi.logstage.sink.slf4j

import java.util.concurrent.ConcurrentHashMap

import izumi.logstage.api.Log
import izumi.logstage.api.logger.LogSink
import izumi.logstage.api.rendering.RenderingPolicy
import org.slf4j
import org.slf4j.Marker
import org.slf4j.helpers.BasicMarkerFactory

class LogSinkLegacySlf4jImpl
(
  policy: RenderingPolicy
) extends LogSink {
  override def flush(e: Log.Entry): Unit = {
    val slf4jLogger = getSlf4jLogger(e)

    e.context.dynamic.level match {
      case Log.Level.Crit =>
        if (slf4jLogger.isErrorEnabled) {
          log(slf4jLogger.error, e)
        }
      case Log.Level.Error =>
        if (slf4jLogger.isErrorEnabled) {
          log(slf4jLogger.error, e)
        }
      case Log.Level.Warn =>
        if (slf4jLogger.isWarnEnabled) {
          log(slf4jLogger.warn, e)
        }
      case Log.Level.Info =>
        if (slf4jLogger.isInfoEnabled) {
          log(slf4jLogger.info, e)
        }
      case Log.Level.Debug =>
        if (slf4jLogger.isDebugEnabled) {
          log(slf4jLogger.debug, e)
        }
      case Log.Level.Trace =>
        if (slf4jLogger.isTraceEnabled) {
          log(slf4jLogger.trace, e)
        }
    }
  }

  private val markerFactory = new BasicMarkerFactory()

  private def log(logger: (Marker, String, Throwable) => Unit, message: Log.Entry): Unit = {
    val throwable = message.firstThrowable
    val asString = policy.render(message)
    val markers = markerFactory.getMarker(s"${message.context.static.position.file}:${message.context.static.position.line}")
    logger(markers, asString, throwable.orNull)
  }

  private val loggers = new ConcurrentHashMap[String, slf4j.Logger]()

  private def getSlf4jLogger(e: Log.Entry): slf4j.Logger = {
    val loggerId = e.context.static.id.id
    loggers.computeIfAbsent(loggerId, (id: String) => slf4j.LoggerFactory.getLogger(id))
  }
}

object LogSinkLegacySlf4jImpl {
  def apply(policy: RenderingPolicy): LogSinkLegacySlf4jImpl = new LogSinkLegacySlf4jImpl(policy)
}
