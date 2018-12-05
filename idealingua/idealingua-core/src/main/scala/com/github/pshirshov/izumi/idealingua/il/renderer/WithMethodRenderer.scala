package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.DefMethod.RPCMethod
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{AdtMember, DefMethod, SimpleStructure}

trait WithMethodRenderer
  extends WithMeta {
  def kw: String

  protected implicit def evSimpleStructure: Renderable[SimpleStructure]

  protected implicit def evTypeId: Renderable[TypeId]

  protected implicit def evAdtMember: Renderable[AdtMember]

  protected def renderMethod(tpe: DefMethod): String = {
    tpe match {
      case m: RPCMethod =>
        val resultRepr = render(m.signature.output).fold("")(s => s": $s")
        val out = s"$kw ${m.name}(${m.signature.input.render()})$resultRepr"
        withMeta(m.meta, out)
    }
  }

  protected def render(out: DefMethod.Output): Option[String] = {
    out match {
      case o: DefMethod.Output.Struct =>
        Some(s"(${o.struct.render()})")
      case o: DefMethod.Output.Algebraic =>
        Some(s"(${o.alternatives.map(_.render()).mkString(" | ")})")
      case o: DefMethod.Output.Singular =>
        Some(o.typeId.render())
      case _: DefMethod.Output.Void =>
        None
      case o: DefMethod.Output.Alternative =>
        Some(s"${render(o.success).getOrElse("()")} !! ${render(o.failure).getOrElse("()")}")
    }
  }
}
