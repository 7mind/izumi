package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._


class IDLRenderer(domain: DomainDefinition) extends IDLRenderers {

  override protected implicit def typeid[T <: TypeId]: Renderable[T] = new RTypeId(domain.id)

  def render(): String = {
    domain.render()
  }
}
