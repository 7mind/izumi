package izumi.idealingua.translator.toscala

import izumi.idealingua.model.il.ast.typed.TypeDef.Adt
import izumi.idealingua.translator.toscala.products.CogenProduct.{AdtElementProduct, AdtProduct}
import izumi.idealingua.translator.toscala.products.RenderableCogenProduct

import scala.meta._

class AdtRenderer(ctx: STContext) {
  import ctx._
  import conv._

  def renderAdt(i: Adt, bases: List[Init] = List.empty): RenderableCogenProduct = {
    val t = conv.toScala(i.id)

    val members = i.alternatives.map {
      m =>
        val memberName = m.typename
        val mt = t.within(memberName)
        val original = conv.toScala(m.typeId)

        val qqElement = q"""final case class ${mt.typeName}(value: ${original.typeAbsolute}) extends ..${List(t.init())}"""
        val qqCompanion = q""" object ${mt.termName} {} """


        val converters = List(
          q"""implicit def ${Term.Name("into" + memberName)}(value: ${original.typeAbsolute}): ${t.typeFull} = ${mt.termFull}(value) """
          ,
          q"""implicit def ${Term.Name("from" + memberName)}(value: ${mt.typeFull}): ${original.typeAbsolute} = value.value"""
        )

        AdtElementProduct(memberName, qqElement, qqCompanion, converters)
    }

    val superClasses = List(rt.adtEl.init()) ++ bases ++ List(conv.toScala[Product].init())
    val qqAdt = q""" sealed trait ${t.typeName} extends ..$superClasses {} """
    val qqAdtCompanion =
      q"""object ${t.termName} extends ${rt.adt.init()} {
            import _root_.scala.language.implicitConversions

            type Element = ${t.typeFull}

           }"""
    ext.extend(i, AdtProduct(qqAdt, qqAdtCompanion, members), _.handleAdt)
  }
}
