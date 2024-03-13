package izumi.logstage.api.logger

import izumi.logstage.api.Log

trait LogQueue extends AutoCloseable {
  def append(entry: Log.Entry, target: LogSink): Unit
  def close(): Unit = ()
}

object LogQueue {
  object Immediate extends LogQueue {
    override def append(entry: Log.Entry, target: LogSink): Unit = {
      target.flush(entry)
    }
  }
}
