package izumi.logstage.sink

import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.*
import izumi.functional.lifecycle.Lifecycle
import izumi.logstage.api.Log
import izumi.logstage.api.logger.{LogQueue, LogSink}

import scala.concurrent.duration.FiniteDuration

class ThreadingLogQueue(sleepTime: FiniteDuration, batchSize: Int) extends LogQueue with AutoCloseable {
  (sleepTime, batchSize).discard()
  def start(): Unit = {}

  override def append(entry: Log.Entry, target: LogSink): Unit = {
    target.flush(entry)
  }

  override def close(): Unit = {}
}

object ThreadingLogQueue {
  def resource(sleepTime: FiniteDuration = scala.concurrent.duration.DurationInt(50).millis, batchSize: Int = 100): Lifecycle[Identity, ThreadingLogQueue] = Lifecycle
    .make[Identity, ThreadingLogQueue] {
      val buffer = new ThreadingLogQueue(sleepTime, batchSize)
      buffer.start()
      buffer
    } {
      buffer => buffer.close()
    }
}
