package izumi.idealingua.translator.toscala

import izumi.idealingua.model.il.ast.typed.Interfaces
import izumi.idealingua.translator.toscala.products.{CogenProduct, RenderableCogenProduct}
import izumi.idealingua.translator.toscala.types.{ClassSource, CompositeStructure, StructContext}

import scala.meta._

class CompositeRenderer(ctx: STContext) {
  import ctx._
  import conv._

  def defns(struct: CompositeStructure, source: ClassSource): RenderableCogenProduct = {
    val withMirror = source match {
      case _: ClassSource.CsInterface =>
        false
      case _ =>
        true
    }

    val bases: List[Init] = source match {
      case _: ClassSource.CsMethodInput =>
        List.empty
//        List(cs.sc.serviceInputBase.init())

      case _: ClassSource.CsMethodOutput =>
        List.empty
//        List(cs.sc.serviceOutputBase.init())

      case _ =>
        List.empty
    }

    val (mirrorInterface: List[Defn.Trait], moreBases: Interfaces) = if (withMirror) {
      val eid = typespace.tools.defnId(struct.fields.id)
      val implStructure = ctx.tools.mkStructure(eid)
      (List(ctx.interfaceRenderer.mkTrait(List.empty, conv.toScala(eid), implStructure.fields)), List(eid))
    } else {
      (List.empty, List.empty)
    }
    val ifDecls = (struct.composite ++ moreBases).map {
      iface =>
        ctx.conv.toScala(iface).init()
    }

    val superClasses = bases ++ ifDecls

    val tools = struct.t.within(s"${struct.fields.id.name.capitalize}Extensions")

    val qqComposite = q"""final case class ${struct.t.typeName}(..${struct.decls}) extends ..$superClasses {}"""

    val toolBases = List(rt.Conversions.parameterize(List(struct.t.typeFull)).init())

    val qqTools = q""" implicit class ${tools.typeName}(override protected val _value: ${struct.t.typeFull}) extends ..$toolBases { }"""


    val qqCompositeCompanion =
      q"""object ${struct.t.termName} {
          ..$mirrorInterface

          ..${struct.constructors}
         }"""

    ext.extend(StructContext(source, struct.fields), CogenProduct(qqComposite, qqCompositeCompanion, qqTools, List.empty), _.handleComposite)
  }

}
