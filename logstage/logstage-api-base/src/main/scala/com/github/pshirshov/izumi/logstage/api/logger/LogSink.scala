package com.github.pshirshov.izumi.logstage.api.logger

import com.github.pshirshov.izumi.logstage.api.Log

trait LogSink extends AutoCloseable {
  def flush(e: Log.Entry): Unit
  def close(): Unit = {}
}




