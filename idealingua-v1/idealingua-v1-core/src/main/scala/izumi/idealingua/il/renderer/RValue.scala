package izumi.idealingua.il.renderer

import izumi.functional.Renderable
import izumi.idealingua.model.common.TypeId
import izumi.idealingua.model.il.ast.typed.ConstValue

class RValue(context: IDLRenderingContext) extends Renderable[ConstValue] {
  import context._

  override def render(v: ConstValue): String = {
    v match {
      case ConstValue.CInt(value) =>
        value.toString
      case ConstValue.CLong(value) =>
        value.toString
      case ConstValue.CFloat(value) =>
        value.toString
      case ConstValue.CBool(value) =>
        value.toString
      case ConstValue.CString(s) =>
        if (s.contains("\"")) {
          "\"\"\"" + s + "\"\"\""
        } else {
          "\"" + s + "\""
        }
      case ConstValue.CMap(value) =>
        value.map {
          case (name, v1: ConstValue.Typed) =>
            s"$name: ${v1.typeId.render()} = ${render(v1)}"
          case (name, v1) =>
            s"$name = ${render(v1)}"
        }.mkString("{", ", ", "}")

      case ConstValue.CList(value) =>
        value.map(render).mkString("[", ", ", "]")

      // TODO: incorrect cases atm
      case ConstValue.CTypedList(typeId, value) =>
        typed(typeId, value.value.map(render).mkString("[", ", ", "]"))

      case ConstValue.CTypedObject(typeId, value) =>
        typed(typeId, value.value.mapValues(render).mkString("[", ",", "]"))

      case ConstValue.CTyped(typeId, value) =>
        typed(typeId, render(value))
        typeId.render() + "(" + render(value) + ")"
    }

  }

  private def typed(typeId: TypeId, value: String) = {
    s"${typeId.render()}($value)"

  }
}
