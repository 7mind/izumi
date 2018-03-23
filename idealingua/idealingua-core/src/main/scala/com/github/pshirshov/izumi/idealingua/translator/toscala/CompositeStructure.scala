package com.github.pshirshov.izumi.idealingua.translator.toscala


import com.github.pshirshov.izumi.idealingua.model.common.TypeId.InterfaceId
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il._

import scala.meta._


// TODO: make it not so ugly
class CompositeStructure(translator: ScalaTranslator, context: ScalaTranslationContext, conv: ScalaTypeConverter, val id: TypeId, val fields: ScalaFields, val composite: ILAst.Composite) {
  val t: ScalaType = conv.toScala(id)

  import ScalaField._
  import conv._

  val explodedSignature = fields.all.toParams
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
          (translator.idToParaName(d), conv.toScala(d).typeFull)
      }
      .toParams

    val fieldParams = fields.nonUnique.toParams

    interfaceParams ++ fieldParams
  }

  def instantiator: Term.Apply = {
    val constructorCode = fields.fields.all.filterNot(f => fields.nonUnique.exists(_.name.value == f.field.name)).map {
      f =>
        q""" ${Term.Name(f.field.name)} = ${translator.idToParaName(f.definedBy)}.${Term.Name(f.field.name)}  """
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
        conv.toScala(iface).init()
    }

    val superClasses = translator.withAnyval(fields.fields, bases ++ ifDecls)
    val tools = t.within(s"${id.name.capitalize}Extensions")

    val converters = translator.mkConverters(id, fields)

    val qqComposite = q"""case class ${t.typeName}(..$decls) extends ..$superClasses {}"""


    val qqCompositeCompanion =
      q"""object ${t.termName} extends ${context.rt.typeCompanionInit} {
              implicit class ${tools.typeName}(_value: ${t.typeFull}) {
                ${context.rt.modelConv.toMethodAst(id)}

                ..$converters
              }

             ..$constructors
         }"""

    translator.withExtensions(id, qqComposite, qqCompositeCompanion, _.handleComposite, _.handleCompositeCompanion)
  }
}
