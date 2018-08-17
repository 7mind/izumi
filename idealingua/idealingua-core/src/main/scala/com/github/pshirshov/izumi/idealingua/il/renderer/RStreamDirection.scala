package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.common.StreamDirection

object RStreamDirection extends Renderable[StreamDirection] {
  override def render(value: StreamDirection): String = {
    value match {
      case StreamDirection.ToServer =>
        "toserver"
      case StreamDirection.ToClient =>
        "toclient"
    }
  }
}
