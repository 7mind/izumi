package izumi.logstage.api.logger

import izumi.logstage.api.Log

trait LogSink {
  def flush(e: Log.Entry): Unit
  def sync(): Unit = ()
}

trait LogQueue extends AutoCloseable {
  def append(entry: Log.Entry, target: LogSink): Unit
  def close(): Unit = ()
}

object LogQueue {
  object LogQueueImmediateImpl extends LogQueue {
    override def append(entry: Log.Entry, target: LogSink): Unit = {
      target.flush(entry)
    }
  }
}
