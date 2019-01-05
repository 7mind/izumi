package com.github.pshirshov.izumi.logstage.api.rendering

import com.github.pshirshov.izumi.logstage.api.Log

trait Renderer {
  def render(entry: Log.Entry): String
}
