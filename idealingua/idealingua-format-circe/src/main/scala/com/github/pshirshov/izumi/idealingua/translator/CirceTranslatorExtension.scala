package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.il.ast.ILAst.{Adt, Enumeration, Identifier, Interface}
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext
import com.github.pshirshov.izumi.idealingua.translator.toscala.extensions.ScalaTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.toscala.tools.ScalaMetaTools._
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.ScalaStruct

import scala.meta._


object CirceTranslatorExtension extends ScalaTranslatorExtension {
  private val imports = List(q""" import _root_.io.circe.{Encoder, Decoder} """)
  private val java8Imports = imports ++ List(
    q""" import _root_.io.circe.java8.time._ """
  )

  override def handleCompositeCompanion(ctx: STContext, struct: ScalaStruct, defn: Defn.Object): Defn.Object = {
    withDerived(ctx, struct.id, defn)
  }

  override def handleAdtCompanion(ctx: STContext, adt: Adt, defn: Defn.Object): Defn.Object = {
    withDerived(ctx, adt.id, defn)
  }

  override def handleAdtElementCompanion(ctx: STContext, id: TypeId.DTOId, defn: Defn.Object): Defn.Object = {
    withDerived(ctx, id, defn)
  }

  override def handleIdentifierCompanion(ctx: STContext, id: Identifier, defn: Defn.Object): Defn.Object = {
    withParseable(ctx, defn, id.id)
  }

  override def handleEnumCompanion(ctx: STContext, enum: Enumeration, defn: Defn.Object): Defn.Object = {
    withParseable(ctx, defn, enum.id)
  }

  override def handleInterfaceCompanion(ctx: STContext, iface: Interface, defn: Defn.Object): Defn.Object = {
    val t = ctx.conv.toScala(iface.id)
    val tpe = t.typeFull
    val implementors = ctx.typespace.implementors(iface.id)

    val enc = implementors.map {
      c =>
        p"""case v: ${ctx.conv.toScala(c.typeToConstruct).typeFull} => Map(${Lit.String(c.typeToConstruct.name)} -> v).asJson"""

    }

    val dec = implementors.map {
      c =>
        p"""case ${Lit.String(c.typeToConstruct.name)} => value.as[${ctx.conv.toScala(c.typeToConstruct).typeFull}]"""
    }

    val circeBoilerplate = java8Imports ++ List(
      q""" import _root_.io.circe.syntax._ """
      ,
      q""" implicit val ${Pat.Var(Term.Name(s"encode${iface.id.name}"))}: Encoder[$tpe] = Encoder.instance {
        c => {
          c match {
            ..case $enc
          }
        }
      } """
      ,
      q""" implicit val ${Pat.Var(Term.Name(s"decode${iface.id.name}"))}: Decoder[$tpe] = Decoder.instance(c => {
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

  private def withParseable(ctx: STContext, defn: Defn.Object, id: TypeId) = {
    val t = ctx.conv.toScala(id)
    val tpe = t.typeFull
    val circeBoilerplate = imports ++ List(
      q""" implicit val ${Pat.Var(Term.Name(s"encode${id.name}"))}: Encoder[$tpe] = Encoder.encodeString.contramap(_.toString) """
      ,
      q""" implicit val ${Pat.Var(Term.Name(s"decode${id.name}"))}: Decoder[$tpe] = Decoder.decodeString.map(${t.termFull}.parse) """
    )
    defn.extendDefinition(circeBoilerplate)
  }

  private def withDerived[T <: Defn](ctx: STContext, id: TypeId, defn: T) = {
    val tpe = ctx.conv.toScala(id).typeFull
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

