package com.github.pshirshov.izumi.idealingua.translator.toscala.types

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.InterfaceId
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.translator.toscala.ScalaTranslationContext

import scala.meta._


class CompositeStructure(ctx: ScalaTranslationContext, val id: StructureId, val fields: ScalaStruct) {
  val t: ScalaType = ctx.conv.toScala(id)

  import ScalaField._
  import ctx.conv._

  private val composite = fields.fields.superclasses.interfaces

  val explodedSignature: List[Term.Param] = fields.all.toParams
  val constructorSignature: List[Term.Param] = {

    val embedded = fields.fields.all
      .map(_.definedBy)
      .collect({ case i: InterfaceId => i })
      .filterNot(composite.contains)
    // TODO: contradictions

    val interfaceParams = (composite ++ embedded)
      .distinct
      .map {
        d =>
          (ctx.tools.idToParaName(d), ctx.conv.toScala(d).typeFull)
      }
      .toParams

    val fieldParams = fields.nonUnique.toParams

    interfaceParams ++ fieldParams
  }

  def instantiator: Term.Apply = {
    val constructorCode = fields.fields.all.filterNot(f => fields.nonUnique.exists(_.name.value == f.field.name)).map {
      f =>
        q""" ${Term.Name(f.field.name)} = ${ctx.tools.idToParaName(f.definedBy)}.${Term.Name(f.field.name)}  """
    }

    val constructorCodeNonUnique = fields.nonUnique.map {
      f =>
        q""" ${f.name} = ${f.name}  """
    }

    q"""
         ${t.termFull}(..${constructorCode ++ constructorCodeNonUnique})
         """

  }

  val constructors: List[Defn.Def] = {

    List(
      q"""def apply(..$constructorSignature): ${t.typeFull} = {
         $instantiator
         }"""
    )

  }

  val decls: List[Term.Param] = fields.all.toParams
  val names: List[Term.Name] = fields.all.toNames

  def defns(bases: List[Init]): Seq[Defn] = {
    val ifDecls = composite.map {
      iface =>
        ctx.conv.toScala(iface).init()
    }

    val superClasses = ctx.tools.withAnyval(fields.fields, bases ++ ifDecls)
    val tools = t.within(s"${id.name.capitalize}Extensions")

    val converters = ctx.tools.mkConverters(id, fields)

    val qqComposite = q"""case class ${t.typeName}(..$decls) extends ..$superClasses {}"""

    val qqTools = q""" implicit class ${tools.typeName}(_value: ${t.typeFull}) { ..$converters }"""
    val extTools = ctx.ext.extend(id, qqTools, _.handleCompositeTools)


    val qqCompositeCompanion =
      q"""object ${t.termName} {
          $extTools
          ..$constructors
         }"""

    ctx.ext.extend(id, qqComposite, qqCompositeCompanion, _.handleComposite, _.handleCompositeCompanion)
  }
}
