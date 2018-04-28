package com.github.pshirshov.izumi.logstage.api.logger

import com.github.pshirshov.izumi.logstage.api.Log


trait LogSink {
  def flush(e: Log.Entry): Unit
}




