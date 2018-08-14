package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.IdField

class RIdField()(implicit ev: Renderable[TypeId]) extends Renderable[IdField] {
  override def render(field: IdField): String = {
    s"${field.name}: ${field.typeId.render()}"
  }
}
