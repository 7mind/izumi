package izumi.logstage.sink

import izumi.logstage.api.Log
import izumi.logstage.api.logger.{LogQueue, LogSink}
import izumi.fundamentals.platform.language.Quirks.*

import scala.concurrent.duration.FiniteDuration

class ThreadingLogQueue(sleepTime: FiniteDuration, batchSize: Int) extends LogQueue with AutoCloseable {
  (sleepTime, batchSize).discard()
  def start(): Unit = {}

  override def append(entry: Log.Entry, target: LogSink): Unit = {
    target.flush(entry)
  }

  override def close(): Unit = {}
}

object ThreadingLogQueue {}
