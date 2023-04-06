package izumi.logstage.sink

import izumi.logstage.api.Log
import izumi.logstage.api.logger.{LogQueue, LogSink}

class ThreadingLogQueue() extends LogQueue with AutoCloseable {
  def start(): Unit = {}

  override def append(entry: Log.Entry, target: LogSink): Unit = {
    target.flush(entry)
  }

  override def close(): Unit = {}
}

object ThreadingLogQueue {}