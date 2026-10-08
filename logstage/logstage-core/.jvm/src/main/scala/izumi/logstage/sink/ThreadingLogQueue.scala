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
  val shutdownHook = new Thread(
    () => {
      stopPolling()
    },
    "logstage-shutdown-hook",
  )

  locally {
    Runtime.getRuntime.addShutdownHook(shutdownHook)
  }

  override protected def unregisterShutdownHook(): Unit = {
    try {
      // removeShutdownHook doesn't work if it gets invoked while hook is running which is exactly the case for termination by signal
      Runtime.getRuntime.removeShutdownHook(shutdownHook)
    } catch {
      case _: IllegalStateException =>
    }

  }
}
