package izumi.logstage.sink

import izumi.fundamentals.platform.IzumiProject
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.console.TrivialLogger.Config
import izumi.fundamentals.platform.language.Quirks.*
import izumi.logstage.DebugProperties
import izumi.logstage.api.Log
import izumi.logstage.api.logger.LogSink

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration.*

class QueueingSink(target: LogSink, sleepTime: FiniteDuration = 50.millis) extends LogSink {
  private val queue = new ConcurrentLinkedQueue[Log.Entry]()
  private val maxBatchSize = 100
  private val stop = new AtomicBoolean(false)

  private val fallback = TrivialLogger.make[FallbackConsoleSink](DebugProperties.`izumi.logstage.routing.log-failures`.name, Config(forceLog = true))

  private val pollingThread = {
    val result = new Thread(new ThreadGroup("logstage"), poller(), "logstage-poll")
    result.setDaemon(true)
    result
  }
  val shutdownHook = new Thread(
    () => {
      stopPolling()
    },
    "logstage-shutdown-hook",
  )

  locally {
    Runtime.getRuntime.addShutdownHook(shutdownHook)
  }

  override def close(): Unit = {
    stopPolling()
  }

  private def stopPolling(): Unit = {
    if (stop.compareAndSet(false, true)) {
      try {
        // removeShutdownHook doesn't work if it gets invoked while hook is running which is exactly the case for termination by signal
        Runtime.getRuntime.removeShutdownHook(shutdownHook)
      } catch {
        case _: IllegalStateException =>
      }

      try {
        pollingThread.join()
      } catch {
        case _: InterruptedException =>
          fallback.log(IzumiProject.bugReportPrompt("LogStage shutdown hook had been interrupted"))
      }

      target.sync()

      if (!queue.isEmpty) {
        fallback.log(IzumiProject.bugReportPrompt("LogStage queue wasn't empty and the end of the polling thread"))
      }
    }
  }

  private def poller(): Runnable = new Runnable {
    override def run(): Unit = {
      while (!stop.get()) {
        try {
          // in case queue was empty we sleep a bit (it's a sane heuristic), otherwise it's better to continue working asap
          if (doFlush(maxBatchSize)) {
            Thread.sleep(sleepTime.toMillis)
          } else {
            Thread.`yield`()
          }
        } catch {
          case _: InterruptedException =>
            stopPolling()

          case e: Throwable => // bad case!
            fallback.log(IzumiProject.bugReportPrompt("LogStage polling loop failed"), e)
        }
      }

      while (!queue.isEmpty) {
        doFlush(Int.MaxValue)
      }
    }
  }

  def start(): Unit = {
    pollingThread.start()
  }

  def flush(e: Log.Entry): Unit = {
    if (!stop.get()) { // Once shutdown is reqested the queue will stop receiving new messages, so it'll become finite
      queue.add(e).discard()
    } else {
      target.flush(e)
      target.sync()
    }
  }

  /**
    * @return true if any messages were processed
    */
  private def doFlush(maxBatch: Int): Boolean = {
    var cc: Int = 0
    var entry: Log.Entry = null

    do {
      entry = queue.poll()
      if (entry != null)
        target.flush(entry)
      cc += 1
    } while (cc <= maxBatch && entry != null)

    entry != null
  }
}
