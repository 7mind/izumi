package com.github.pshirshov.izumi.logstage.api.logger

import com.github.pshirshov.izumi.logstage.model.Log

case class RenderingOptions(
                             withExceptions: Boolean = true
                             , withColors: Boolean = true
                           )

trait RenderingPolicy {
  def render(entry: Log.Entry): String
}
