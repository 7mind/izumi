package izumi.logstage.sink

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.console.TrivialLogger.Config
import izumi.fundamentals.platform.language.Quirks._
import izumi.logstage.DebugProperties
import izumi.logstage.api.Log
import izumi.logstage.api.logger.LogSink

import scala.concurrent.duration._

class QueueingSink(target: LogSink, sleepTime: FiniteDuration = 50.millis) extends LogSink {

  import QueueingSink._

  private val queue = new ConcurrentLinkedQueue[Log.Entry]
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

      while (!queue.isEmpty) {
        doFlush(NullStep)
      }
      target.sync()
    }
  }

  private def poller(): Runnable = new Runnable {
    override def run(): Unit = {
      while (!stop.get()) { // it's fine to spin forever, we are running in a daemon thread, it'll exit once the app finishes
        try {
          val step = new CountingStep(maxBatchSize)
          // in case queue was empty we sleep a bit (it's a sane heuristic), otherwise it's better to continue working asap
          if (doFlush(step) == null) {
            Thread.sleep(sleepTime.toMillis)
          } else {
            Thread.`yield`()
          }
        } catch {
          case _: InterruptedException =>
            stopPolling()

          case e: Throwable => // bad case!
            fallback.log("Logger polling failed", e)
        }
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

  private def doFlush(step: Step): Log.Entry = {
    var entry = queue.poll()

    while (entry != null && step.continue) {
      target.flush(entry)
      entry = queue.poll()
      step.onStep()
    }

    entry
  }

}

object QueueingSink {

  trait Step {
    def continue: Boolean

    def onStep(): Unit
  }

  class CountingStep(max: Int) extends Step {
    var counter: Int = 0

    override def continue: Boolean = counter <= max

    override def onStep(): Unit = counter += 1
  }

  object NullStep extends Step {
    override def continue: Boolean = true

    override def onStep(): Unit = {}
  }

}
