package izumi.logstage.sink

import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.Log
import izumi.logstage.api.logger.LogSink

import scala.concurrent.duration.*

object ThreadingLogQueue {
  case class LoggingAction(entry: Log.Entry, target: LogSink)

  def resource(sleepTime: FiniteDuration = 50.millis, batchSize: Int = 100): Lifecycle[Identity, ThreadingLogQueue] = {
    Lifecycle.fromAutoCloseable[ThreadingLogQueue] {
      val buffer = new ThreadingLogQueue(sleepTime, batchSize)
      buffer.start()
      buffer
    }
  }
}

class ThreadingLogQueue(sleepTime: FiniteDuration, batchSize: Int) extends ThreadingLogQueueBase(sleepTime, batchSize) {
  lazy val shutdownHook: Thread = unsupportedShutdownHook()

  private def unsupportedShutdownHook(): Thread = {
    throw new UnsupportedOperationException(
      "Automatic shutdown draining is unavailable on Scala Native; use ThreadingLogQueue.resource or close the queue explicitly"
    )
  }

  override protected def unregisterShutdownHook(): Unit = ()
}
