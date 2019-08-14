package izumi.idealingua.il.renderer

import izumi.functional.Renderable
import izumi.idealingua.model.common.StreamDirection

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
