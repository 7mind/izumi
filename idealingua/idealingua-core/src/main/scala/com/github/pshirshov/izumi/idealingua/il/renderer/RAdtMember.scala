package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.AdtMember

class RAdtMember()(implicit ev: Renderable[TypeId]) extends Renderable[AdtMember] {
  override def render(field: AdtMember): String = {
    val t = field.typeId.render()
    field.memberName match {
      case Some(name) =>
        s"$t as $name"
      case None =>
        t
    }
  }
}
