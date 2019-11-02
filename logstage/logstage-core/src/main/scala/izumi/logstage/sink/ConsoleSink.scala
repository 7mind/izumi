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

  final val ColoredConsoleSink = new ConsoleSink(coloringPolicy())
  final val SimpleConsoleSink = new ConsoleSink(simplePolicy())

  protected[logstage] def coloringPolicy(renderingLayout: Option[Renderer.Aggregate] = None): StringRenderingPolicy = {
    new StringRenderingPolicy(RenderingOptions(), renderingLayout)
  }

  protected[logstage] def simplePolicy(renderingLayout: Option[Renderer.Aggregate] = None): StringRenderingPolicy = {
    new StringRenderingPolicy(RenderingOptions(withExceptions = false, colored = false), renderingLayout)
  }
}
