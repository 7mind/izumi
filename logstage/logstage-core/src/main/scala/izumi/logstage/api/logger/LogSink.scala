package izumi.logstage.api.logger

import izumi.logstage.api.Log

trait LogSink extends AutoCloseable {
  def flush(e: Log.Entry): Unit
  def close(): Unit = ()
}
