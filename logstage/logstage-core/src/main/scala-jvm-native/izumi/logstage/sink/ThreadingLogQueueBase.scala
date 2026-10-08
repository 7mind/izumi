package izumi.logstage.sink

import izumi.fundamentals.platform.IzumiProject
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.console.TrivialLogger.Config
import izumi.fundamentals.platform.language.Quirks.*
import izumi.logstage.DebugProperties
import izumi.logstage.api.Log
import izumi.logstage.api.logger.{LogQueue, LogSink}

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration.*

abstract class ThreadingLogQueueBase(sleepTime: FiniteDuration, batchSize: Int) extends LogQueue with AutoCloseable {
  private val queue = new ConcurrentLinkedQueue[ThreadingLogQueue.LoggingAction]()
  private val stop = new AtomicBoolean(false)

  private val fallback = TrivialLogger.make[ThreadingLogQueue](DebugProperties.`izumi.logstage.routing.log-failures`.name, Config(forceLog = true))

  private val pollingThread = {
    val result = new Thread(new ThreadGroup("logstage"), poller(), "logstage-poll")
    result.setDaemon(true)
    result
  }
  protected def unregisterShutdownHook(): Unit

  override def close(): Unit = {
    stopPolling()
  }

  protected final def stopPolling(): Unit = {
    if (stop.compareAndSet(false, true)) {
      unregisterShutdownHook()

      try {
        pollingThread.join()
      } catch {
        case _: InterruptedException =>
          fallback.log(IzumiProject.bugReportPrompt("LogStage shutdown hook had been interrupted"))
      }

      // target.sync()

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
          if (doFlush(batchSize)) {
            Thread.`yield`()
          } else {
            Thread.sleep(sleepTime.toMillis)
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

  /**
    * @return true if any messages were processed
    */
  private def doFlush(maxBatch: Int): Boolean = {
    var cc: Int = 0
    var entry: ThreadingLogQueue.LoggingAction = null

    while ({
      entry = queue.poll()
      if (entry != null)
        entry.target.flush(entry.entry)
      cc += 1
      cc <= maxBatch && entry != null
    }) {}

    entry != null
  }

  override def append(entry: Log.Entry, target: LogSink): Unit = {
    if (!stop.get()) { // Once shutdown is reqested the queue will stop receiving new messages, so it'll become finite
      queue.add(ThreadingLogQueue.LoggingAction(entry, target)).discard()
    } else {
      target.flush(entry)
      target.sync()
    }
  }
}
