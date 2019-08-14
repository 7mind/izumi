package izumi.logstage.api.rendering

import izumi.logstage.api.Log

trait RenderingPolicy {
  def render(entry: Log.Entry): String
}
