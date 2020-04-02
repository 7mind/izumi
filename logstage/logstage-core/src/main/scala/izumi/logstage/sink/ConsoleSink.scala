package izumi.logstage.sink

import izumi.logstage.api.Log
import izumi.logstage.api.logger.LogSink
import izumi.logstage.api.rendering.logunits.Renderer
import izumi.logstage.api.rendering.{RenderingOptions, RenderingPolicy, StringRenderingPolicy}

class ConsoleSink(policy: RenderingPolicy) extends LogSink {
  override def flush(e: Log.Entry): Unit = {
    System.out.println(policy.render(e))
  }
}

object ConsoleSink {
  def apply(policy: RenderingPolicy): ConsoleSink = new ConsoleSink(policy)
  def text(colored: Boolean = true): ConsoleSink = if (colored) ColoredConsoleSink else SimpleConsoleSink

  object ColoredConsoleSink extends ConsoleSink(coloringPolicy())
  object SimpleConsoleSink extends ConsoleSink(simplePolicy())

  private[logstage] def coloringPolicy(renderingLayout: Option[Renderer.Aggregate] = None): StringRenderingPolicy =
    new StringRenderingPolicy(RenderingOptions.default, renderingLayout)
  private[logstage] def simplePolicy(renderingLayout: Option[Renderer.Aggregate] = None): StringRenderingPolicy =
    new StringRenderingPolicy(RenderingOptions.simple, renderingLayout)
}
