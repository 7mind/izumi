package com.github.pshirshov.izumi.logstage.api.routing

import com.github.pshirshov.izumi.logstage.api.logger.RenderingPolicy
import com.github.pshirshov.izumi.logstage.model.Log
import com.github.pshirshov.izumi.logstage.model.logger.{FallbackLogOutput, LogSink}

class FallbackConsoleSink(policy: RenderingPolicy) extends LogSink {
  override def flush(e: Log.Entry): Unit = {
    FallbackLogOutput.flush(policy.render(e))
  }
}



