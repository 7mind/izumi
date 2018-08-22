package com.github.pshirshov.izumi.logstage.sink

import com.github.pshirshov.izumi.fundamentals.platform.console.SystemOutStringTrivialSink
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.logger.LogSink
import com.github.pshirshov.izumi.logstage.api.rendering.{RenderingOptions, RenderingPolicy, StringRenderingPolicy}
import com.github.pshirshov.izumi.logstage.config.codecs.LogSinkCodec.LogSinkMapper
import com.typesafe.config.Config

class ConsoleSink(policy: RenderingPolicy) extends LogSink {
  override def flush(e: Log.Entry): Unit = {
    SystemOutStringTrivialSink.flush(policy.render(e))
  }
}

object ConsoleSink {
  final val ColoredConsoleSink = new ConsoleSink(coloringPolicy())
  final val SimpleConsoleSink = new ConsoleSink(simplePolicy())

  protected[logstage] def coloringPolicy(renderingLayout: Option[String] = None): StringRenderingPolicy = {
    new StringRenderingPolicy(RenderingOptions(), renderingLayout)
  }

  protected[logstage] def simplePolicy(renderingLayout: Option[String] = None): StringRenderingPolicy = {
    new StringRenderingPolicy(RenderingOptions(withExceptions = false, withColors = false), renderingLayout)
  }

  val configInstanceMapper: LogSinkMapper[ConsoleSink] = new LogSinkMapper[ConsoleSink] {
    override def instantiate(config: Config, renderingPolicy: RenderingPolicy): ConsoleSink = {
      Quirks.discard(config)
      new ConsoleSink(renderingPolicy)
    }
  }
}
