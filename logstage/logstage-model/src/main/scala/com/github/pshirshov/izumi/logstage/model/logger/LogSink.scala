package com.github.pshirshov.izumi.logstage.model.logger

import com.github.pshirshov.izumi.logstage.model.Log


trait LogSink {
  def flush(e: Log.Entry): Unit
}



