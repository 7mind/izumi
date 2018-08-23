package com.github.pshirshov.izumi.logstage.sink

import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.logger.LogSink
import com.github.pshirshov.izumi.logstage.api.rendering.RenderingPolicy

class FallbackConsoleSink(policy: RenderingPolicy, trivialLogger: TrivialLogger) extends LogSink {
  override def flush(e: Log.Entry): Unit = {
    trivialLogger.log(policy.render(e))
  }
}
