package com.github.pshirshov.izumi.logstage.sink.console

import com.github.pshirshov.izumi.fundamentals.platform.console.SystemOutStringTrivialSink
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.logger.LogSink
import com.github.pshirshov.izumi.logstage.api.rendering.RenderingPolicy

class ConsoleSink(policy: RenderingPolicy) extends LogSink {
  override def flush(e: Log.Entry): Unit = {
    SystemOutStringTrivialSink.flush(policy.render(e))
  }
}
