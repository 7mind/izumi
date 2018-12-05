package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.common.TypeId

class RTypeDef()(
  implicit ev1: Renderable[TypeId]
  , ev2: Renderable[AdtMember]
  , ev3: Renderable[Structure]
  , ev4: Renderable[IdField]
  , protected val evAnno: Renderable[Anno]
) extends Renderable[TypeDef] with WithMeta {
  override def render(tpe: TypeDef): String = {
    val struct = tpe match {
      case d: Adt =>
        s"""adt ${d.id.render()} {
           |${d.alternatives.map(_.render()).mkString("\n").shift(2)}
           |}
         """.stripMargin

      case d: Enumeration =>
        s"""enum ${d.id.render()} {
           |${d.members.map(renderEnumMember).mkString("\n").shift(2)}
           |}
         """.stripMargin

      case d: Alias =>
        s"alias ${d.id.render()} = ${d.target.render()}\n"

      case d: Identifier =>
        s"""id ${d.id.render()} {
           |${renderPrimitiveAggregate(d.fields).shift(2)}
           |}
         """.stripMargin

      case d: Interface =>
        val body = d.struct.render()

        s"""mixin ${d.id.render()} {
           |${body.shift(2)}
           |}
         """.stripMargin

      case d: DTO =>
        val body = d.struct.render()

        s"""data ${d.id.render()} {
           |${body.shift(2)}
           |}
         """.stripMargin
    }
    withMeta(tpe.meta, struct)
  }

  private def renderPrimitiveAggregate(aggregate: IdTuple): String = {
    aggregate
      .map(_.render())
      .mkString("\n")
  }

  private def renderEnumMember(s: EnumMember): String = {
    withMeta(s.meta, s.value)
  }
}
