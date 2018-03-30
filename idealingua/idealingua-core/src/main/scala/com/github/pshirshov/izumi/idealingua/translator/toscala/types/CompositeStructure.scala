package com.github.pshirshov.izumi.idealingua.translator.toscala.types

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.InterfaceId
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext
import com.github.pshirshov.izumi.idealingua.translator.toscala.extensions.CogenProduct

import scala.meta._


class CompositeStructure(ctx: STContext, val fields: ScalaStruct) {
  val t: ScalaType = ctx.conv.toScala(fields.id)

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
      }.toParams


    val fieldParams = fields.localOrAmbigious.toParams

    interfaceParams ++
      fieldParams
  }

  def instantiator: Term.Apply = {
    val local = fields.localOrAmbigious
    val localNames = local.map(_.field.field.name).toSet

    val constructorCode = fields.fields.all
      .filterNot(f => localNames.contains(f.field.name))
      .map {
        f =>
          q""" ${Term.Name(f.field.name)} = ${ctx.tools.idToParaName(f.definedBy)}.${Term.Name(f.field.name)}  """
      }

    val constructorCodeNonUnique = local.distinct.map {
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
    val tools = t.within(s"${fields.id.name.capitalize}Extensions")

    val qqComposite = q"""case class ${t.typeName}(..$decls) extends ..$superClasses {}"""

    val qqTools = q""" implicit class ${tools.typeName}(_value: ${t.typeFull}) { }"""

    val qqCompositeCompanion =
      q"""object ${t.termName} {
          ..$constructors
         }"""

    ctx.ext.extend(fields, CogenProduct(qqComposite, qqCompositeCompanion, qqTools), _.handleComposite).render
  }
}
