package izumi.idealingua.translator.toscala

import izumi.idealingua.model.il.ast.typed.Interfaces
import izumi.idealingua.model.il.ast.typed.TypeDef.Interface
import izumi.idealingua.translator.toscala.products.CogenProduct.TraitProduct
import izumi.idealingua.translator.toscala.products.{CogenProduct, RenderableCogenProduct}
import izumi.idealingua.translator.toscala.types.{ClassSource, ScalaStruct, ScalaType}

import scala.meta._

class InterfaceRenderer(ctx: STContext) {
  import ctx._
  import conv._

  def renderInterface(i: Interface): RenderableCogenProduct = {
    val fields = typespace.structure.structure(i).toScala
    val t = conv.toScala(i.id)

    val qqInterface = mkTrait(i.struct.superclasses.interfaces, t, fields)

    val eid = typespace.tools.implId(i.id)
    val implStructure = ctx.tools.mkStructure(eid)
    val impl = compositeRenderer.defns(implStructure, ClassSource.CsInterface(i)).render
    val qqInterfaceCompanion =
      q"""object ${t.termName} {
             def apply(..${implStructure.decls}) = ${conv.toScala(eid).termName}(..${implStructure.names})
             ..$impl
         }"""


    val toolBases = List(rt.Conversions.parameterize(List(t.typeFull)).init())

    val tools = t.within(s"${i.id.name}Extensions")
    val qqTools = q"""implicit class ${tools.typeName}(override protected val _value: ${t.typeFull}) extends ..$toolBases { }"""

    ext.extend(i, CogenProduct(qqInterface, qqInterfaceCompanion, qqTools, List.empty), _.handleInterface)
  }

  def mkTrait(supers: Interfaces, t: ScalaType, fields: ScalaStruct): Defn.Trait = {
    val decls = fields.all.map {
      f =>
        Decl.Def(List.empty, f.name, List.empty, List.empty, f.fieldType)
    }

    val ifDecls = (rt.generated +: supers.map(conv.toScala)).map(_.init())


    val qqInterface =
      q"""trait ${t.typeName} extends ..$ifDecls {
          ..$decls
          }
       """

    ext.extend(fields, TraitProduct(qqInterface), _.handleTrait).defn
  }
}
