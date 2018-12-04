package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Value

class RValue()(
  implicit protected val evTypeId: Renderable[TypeId]
) extends Renderable[Value] {

  override def render(v: Value): String = {
    v match {
      case Value.CInt(value) =>
        value.toString
      case Value.CLong(value) =>
        value.toString
      case Value.CFloat(value) =>
        value.toString
      case Value.CBool(value) =>
        value.toString
      case Value.CString(s) =>
        if (s.contains("\"")) {
          "\"\"\"" + s + "\"\"\""
        } else {
          "\"" + s + "\""
        }
      case Value.CMap(value) =>
        value.map {
          case (name, v1: Value.Typed) =>
            s"$name: ${evTypeId.render(v1.typeId)} = ${render(v1)}"
          case (name, v1) =>
            s"$name = ${render(v1)}"
        }.mkString("{", ", ", "}")

      case Value.CList(value) =>
        value.map(render).mkString("[", ", ", "]")

      // TODO: incorrect cases atm
      case Value.CTypedList(typeId, value) =>
        typed(typeId, value.value.map(render).mkString("[", ", ", "]"))

      case Value.CTypedObject(typeId, value) =>
        typed(typeId, value.value.mapValues(render).mkString("[", ",", "]"))


      case Value.CTyped(typeId, value) =>
        typed(typeId, render(value))
        evTypeId.render(typeId) + "(" + render(value) + ")"
    }

  }

  private def typed(typeId: TypeId, value: String) = {
    s"${evTypeId.render(typeId)}($value)"

  }
}



