package com.github.pshirshov.izumi.logstage.sink

import com.github.pshirshov.izumi.fundamentals.platform.console.SystemOutStringTrivialSink
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.logger.LogSink
import com.github.pshirshov.izumi.logstage.api.rendering.{RenderingOptions, Renderer, StringRenderer}

class ConsoleSink(policy: Renderer) extends LogSink {
  override def flush(e: Log.Entry): Unit = {
    SystemOutStringTrivialSink.flush(policy.render(e))
  }
}

object ConsoleSink {
  def apply(policy: Renderer): ConsoleSink = new ConsoleSink(policy)

  def text(colored: Boolean = true): ConsoleSink = if (colored) ColoredConsoleSink else SimpleConsoleSink

  final val ColoredConsoleSink = new ConsoleSink(coloringPolicy())
  final val SimpleConsoleSink = new ConsoleSink(simplePolicy())

  protected[logstage] def coloringPolicy(renderingLayout: Option[String] = None): StringRenderer = {
    new StringRenderer(RenderingOptions(), renderingLayout)
  }

  protected[logstage] def simplePolicy(renderingLayout: Option[String] = None): StringRenderer = {
    new StringRenderer(RenderingOptions(withExceptions = false, withColors = false), renderingLayout)
  }
}
