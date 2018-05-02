package com.github.pshirshov.izumi.logstage.api.rendering

import com.github.pshirshov.izumi.logstage.api.Log



trait RenderingPolicy {
  def render(entry: Log.Entry): String
}
