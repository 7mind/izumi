package com.github.pshirshov.izumi.logstage.core

import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger
import com.github.pshirshov.izumi.logstage.api.logger.RenderingPolicy
import com.github.pshirshov.izumi.logstage.model.Log
import com.github.pshirshov.izumi.logstage.model.logger.LogSink

class FallbackConsoleSink(policy: RenderingPolicy, trivialLogger: TrivialLogger) extends LogSink {
  override def flush(e: Log.Entry): Unit = {
    trivialLogger.log(policy.render(e))
  }
}

object FallbackConsoleSink {
  final val fallbackPropertyName = "izumi.logstage.routing.log-failures"
}
