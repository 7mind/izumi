package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Field

class RField()(implicit ev: Renderable[TypeId]) extends Renderable[Field] {

  override def render(field: Field): String = {
    s"${field.name}: ${field.typeId.render()}"
  }
}
