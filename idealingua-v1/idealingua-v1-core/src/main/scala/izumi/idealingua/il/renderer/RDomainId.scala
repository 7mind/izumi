package izumi.idealingua.il.renderer

import izumi.functional.Renderable
import izumi.idealingua.model.common.DomainId

object RDomainId extends Renderable[DomainId] {
  override def render(value: DomainId): String = {
    value.toPackage.mkString(".")
  }
}
