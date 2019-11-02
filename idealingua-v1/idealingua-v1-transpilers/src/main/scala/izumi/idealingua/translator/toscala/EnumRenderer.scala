package izumi.idealingua.translator.toscala

import izumi.idealingua.model.il.ast.typed.TypeDef.Enumeration
import izumi.idealingua.translator.toscala.products.CogenProduct.EnumProduct
import izumi.idealingua.translator.toscala.products.RenderableCogenProduct

import scala.meta._

class EnumRenderer(ctx: STContext) {
  import ctx._
  import conv._

  def renderEnumeration(i: Enumeration): RenderableCogenProduct = {
    val t = conv.toScala(i.id)

    val members = i.members.map {
      m =>
        val mt = t.within(m.value)
        val element =
          q"""final case object ${mt.termName} extends ${t.init()} {
              override def toString: String = ${Lit.String(m.value)}
            }"""

        mt.termName -> element
    }

    val parseMembers = members.map {
      case (termName, _) =>
        val termString = termName.value
        p"""case ${Lit.String(termString)} => $termName"""
    }

    val qqEnum = q""" sealed trait ${t.typeName} extends ${rt.enumEl.init()} {} """
    val qqEnumCompanion =
      q"""object ${t.termName} extends ${rt.enum.init()} {
            type Element = ${t.typeFull}

            override def all: Seq[${t.typeFull}] = Seq(..${members.map(_._1)})

            override def parse(value: String): ${t.typeName} = value match {
              ..case $parseMembers
            }
           }"""

    ext.extend(i, EnumProduct(qqEnum, qqEnumCompanion, members), _.handleEnum)
  }


}
