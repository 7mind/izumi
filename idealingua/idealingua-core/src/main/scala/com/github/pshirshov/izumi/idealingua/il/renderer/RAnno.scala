package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{Anno, Value}

class RAnno()(
  implicit protected val evTypeId: Renderable[TypeId]
  , protected val evValue: Renderable[Value]
) extends Renderable[Anno] {
  override def render(anno: Anno): String = {
    val vals = anno.values.map {
      case (name, v: Value.Typed) =>
        s"$name: ${evTypeId.render(v.typeId)} = ${evValue.render(v)}"
      case (name, v) =>
        s"$name = ${evValue.render(v)}"
    }.mkString(", ")

    s"@${anno.name}($vals)"
  }
}
