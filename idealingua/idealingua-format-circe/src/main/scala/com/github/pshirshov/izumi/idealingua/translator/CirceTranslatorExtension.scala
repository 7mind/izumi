package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.translator.toscala.ScalaMetaTools._
import com.github.pshirshov.izumi.idealingua.translator.toscala.{ScalaTranslationContext, ScalaTranslatorExtension}

import scala.meta.{Defn, _}

object CirceTranslatorExtension extends ScalaTranslatorExtension {
  private val imports = List(q""" import _root_.io.circe.{Encoder, Decoder} """)
  private val java8Imports = imports ++ List(
    q""" import _root_.io.circe.java8.time._ """
  )

  override def handleCompositeCompanion(context: ScalaTranslationContext, id: TypeId, defn: Defn.Object): Defn.Object = {
    withDerived(context, id, defn)
  }

  override def handleIdentifierCompanion(context: ScalaTranslationContext, id: TypeId.IdentifierId, defn: Defn.Object): Defn.Object = {
    withDerived(context, id, defn)
  }

  override def handleAdtCompanion(context: ScalaTranslationContext, id: TypeId.AdtId, defn: Defn.Object): Defn.Object = {
    withDerived(context, id, defn)
  }

  override def handleAdtElementCompanion(context: ScalaTranslationContext, id: TypeId.EphemeralId, defn: Defn.Object): Defn.Object = {
    withDerived(context, id, defn)
  }

  override def handleEnumCompanion(context: ScalaTranslationContext, id: TypeId.EnumId, defn: Defn.Object): Defn.Object = {
    val t = context.conv.toScala(id)
    val tpe = t.typeFull
    val circeBoilerplate = imports ++ List(
      q""" implicit val ${Pat.Var(Term.Name(s"encode${id.name}"))}: Encoder[$tpe] = Encoder.encodeString.contramap(_.toString) """
      ,
      q""" implicit val ${Pat.Var(Term.Name(s"decode${id.name}"))}: Decoder[$tpe] = Decoder.decodeString.map(${t.termFull}.parse) """
    )
    defn.extendDefinition(circeBoilerplate)
  }

  override def handleInterfaceCompanion(context: ScalaTranslationContext, id: TypeId.InterfaceId, defn: Defn.Object): Defn.Object = {
    val t = context.conv.toScala(id)
    val tpe = t.typeFull
    val implementors = context.typespace.implementors(id)
    println(id, implementors)
    val enc = implementors.map {
      c =>
        p"""case v: ${context.conv.toScala(c.typeToConstruct).typeFull} => Map(${Lit.String(c.typeToConstruct.name)} -> v).asJson"""

    }

    val dec = implementors.map {
      c =>
        p"""case ${Lit.String(c.typeToConstruct.name)} => value.as[${context.conv.toScala(c.typeToConstruct).typeFull}]"""
    }

    val circeBoilerplate = java8Imports ++ List(
      q""" import _root_.io.circe.syntax._ """
      ,
      q""" implicit val ${Pat.Var(Term.Name(s"encode${id.name}"))}: Encoder[$tpe] = Encoder.instance {
        c => {
          c match {
            ..case $enc
          }
        }
      } """
      ,
      q""" implicit val ${Pat.Var(Term.Name(s"decode${id.name}"))}: Decoder[$tpe] = Decoder.instance(c => {
             val fname = c.keys.flatMap(_.headOption).toSeq.head
             val value = c.downField(fname)
             fname match {
               ..case $dec
             }
           })
       """
    )
    defn.extendDefinition(circeBoilerplate)
  }

  private def withDerived[T <: Defn](context: ScalaTranslationContext, id: TypeId, defn: T) = {
    val tpe = context.conv.toScala(id).typeFull
    val circeBoilerplate = java8Imports ++ List(
      q""" import _root_.io.circe.generic.semiauto.{deriveDecoder, deriveEncoder} """
      ,
      q""" implicit val ${Pat.Var(Term.Name(s"encode${id.name}"))}: Encoder[$tpe] = deriveEncoder[$tpe] """
      ,
      q""" implicit val ${Pat.Var(Term.Name(s"decode${id.name}"))}: Decoder[$tpe] = deriveDecoder[$tpe] """
    )
    defn.extendDefinition(circeBoilerplate)
  }
}

