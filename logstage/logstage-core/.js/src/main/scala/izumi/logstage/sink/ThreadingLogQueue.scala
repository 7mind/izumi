package izumi.logstage.sink

import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.Log
import izumi.logstage.api.logger.{LogQueue, LogSink}

import scala.annotation.unused
import scala.collection.mutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class ThreadingLogQueue(@unused sleepTime: FiniteDuration, @unused batchSize: Int) extends LogQueue with AutoCloseable {
  private var started = false
  private var queuedMsgs: mutable.ListBuffer[(LogSink, Log.Entry)] = null

  def start(): Unit = {
    synchronized {
      started = true
      flushQueued()
    }
  }

  override def append(entry: Log.Entry, target: LogSink): Unit = {
    if (started) {
      flushQueued()
      target.flush(entry)
    } else {
      synchronized {
        if (queuedMsgs eq null) {
          queuedMsgs = mutable.ListBuffer.empty
        }
        queuedMsgs += (target -> entry)
        ()
      }
    }
  }

  override def close(): Unit = {
    synchronized {
      flushQueued()
      started = false
    }
  }

  protected def flushQueued(): Unit = {
    synchronized {
      if ((queuedMsgs ne null) && queuedMsgs.nonEmpty) {
        queuedMsgs.foreach { case (tgt, ent) => tgt.flush(ent) }
        queuedMsgs.remove(0, queuedMsgs.size)
      }
    }
  }
}

object ThreadingLogQueue {
  def resource(sleepTime: FiniteDuration = 50.millis, batchSize: Int = 100): Lifecycle[Identity, ThreadingLogQueue] = {
    Lifecycle.fromAutoCloseable[ThreadingLogQueue] {
      val buffer = new ThreadingLogQueue(sleepTime, batchSize)
      buffer.start()
      buffer
    }
  }
}
