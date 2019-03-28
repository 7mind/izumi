package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.common.DomainId

object RDomainId extends Renderable[DomainId] {
  override def render(value: DomainId): String = {
    value.toPackage.mkString(".")
  }
}
