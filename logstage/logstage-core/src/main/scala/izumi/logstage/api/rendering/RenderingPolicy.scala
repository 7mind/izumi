package izumi.logstage.api.rendering

import izumi.logstage.api.Log
import izumi.logstage.api.rendering.logunits.Renderer

trait RenderingPolicy {
  def render(entry: Log.Entry): String
}

object RenderingPolicy {
  def coloringPolicy(renderingLayout: Option[Renderer.Aggregate] = None): StringRenderingPolicy = {
    new StringRenderingPolicy(RenderingOptions.default, renderingLayout)
  }
  def colorlessPolicy(renderingLayout: Option[Renderer.Aggregate] = None): StringRenderingPolicy = {
    new StringRenderingPolicy(RenderingOptions.colorless, renderingLayout)
  }
  def simplePolicy(renderingLayout: Option[Renderer.Aggregate] = None): StringRenderingPolicy = {
    new StringRenderingPolicy(RenderingOptions.simple, renderingLayout)
  }
}
