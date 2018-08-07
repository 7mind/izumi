package com.github.pshirshov.izumi.logstage.sink

import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.logger.LogSink
import com.github.pshirshov.izumi.logstage.api.rendering.RenderingPolicy
import com.github.pshirshov.izumi.logstage.config.codecs.LogSinkCodec.LogSinkMapper
import com.typesafe.config.Config

class FallbackConsoleSink(policy: RenderingPolicy, trivialLogger: TrivialLogger) extends LogSink {
  override def flush(e: Log.Entry): Unit = {
    trivialLogger.log(policy.render(e))
  }
}

//object ConsoleSink {
//  val configInstanceMapper : LogSinkMapper[ConsoleSink] = new LogSinkMapper[ConsoleSink] {
//    override def instantiate(config: Config, renderingPolicy: RenderingPolicy): ConsoleSink = {
//      Quirks.discard(config)
//      new ConsoleSink(renderingPolicy)
//    }
//  }
//}
