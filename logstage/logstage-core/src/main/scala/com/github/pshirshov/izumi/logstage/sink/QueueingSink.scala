package com.github.pshirshov.izumi.logstage.sink

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.logger.{LogRouter, LogSink}
import com.github.pshirshov.izumi.logstage.sink.QueueingSink.CountingStep

import scala.concurrent.duration._


class QueueingSink(target: LogSink, sleepTime: FiniteDuration = 50.millis)
  extends LogSink with AutoCloseable {
  private val queue = new ConcurrentLinkedQueue[Log.Entry]()
  private val maxBatchSize = 100
  private val stop = new AtomicBoolean(false)

  private val fallback = TrivialLogger.make[FallbackConsoleSink](LogRouter.fallbackPropertyName, forceLog = true)
  private val pollingThread = {
    val result = new Thread(new ThreadGroup("logstage"), poller(), "logstage-poll")
    result.setDaemon(true)
    result
  }

  private def poller(): Runnable = new Runnable {
    override def run(): Unit = {
      while (!stop.get()) {
        try {
          // in case queue was empty we (probably) may sleep, otherwise it's better to continue working asap
          if (doFlush(new CountingStep(maxBatchSize)) == null) {
            Thread.sleep(sleepTime.toMillis)
          } else {
            Thread.`yield`()
          }
        } catch {
          case _: InterruptedException =>
            stop.set(true)

          case e: Throwable => // bad case!
            fallback.log("Logger polling failed", e)
        }
      }

      finish()
    }
  }
  import QueueingSink._

  def start(): Unit = {
    pollingThread.start()
  }

  override def close(): Unit = {
    if (stop.compareAndSet(false, true)) {
      pollingThread.join()
      finish()
    }
  }

  private def finish(): Unit = {
    doFlush(NullStep).discard()
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

  override def flush(e: Log.Entry): Unit = {
    queue.add(e).discard()
    if (stop.get()) {
      finish()
    }
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
