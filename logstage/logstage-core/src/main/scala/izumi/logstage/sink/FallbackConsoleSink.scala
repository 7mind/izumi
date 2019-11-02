package izumi.logstage.sink

import izumi.fundamentals.platform.console.TrivialLogger
import izumi.logstage.api.Log
import izumi.logstage.api.logger.LogSink
import izumi.logstage.api.rendering.RenderingPolicy

class FallbackConsoleSink(policy: RenderingPolicy, trivialLogger: TrivialLogger) extends LogSink {
  override def flush(e: Log.Entry): Unit = {
    trivialLogger.log(policy.render(e))
  }
}
