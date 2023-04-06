package izumi.logstage.api.logger

import izumi.logstage.api.Log

trait LogSink {
  def flush(e: Log.Entry): Unit
  def sync(): Unit = ()
}




