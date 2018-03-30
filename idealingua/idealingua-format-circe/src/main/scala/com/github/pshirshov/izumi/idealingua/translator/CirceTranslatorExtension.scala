package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.DTOId
import com.github.pshirshov.izumi.idealingua.model.il.ast.ILAst.{Adt, Enumeration, Identifier, Interface}
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext
import com.github.pshirshov.izumi.idealingua.translator.toscala.extensions.CogenProduct.{CompositeProudct, IdentifierProudct, InterfaceProduct}
import com.github.pshirshov.izumi.idealingua.translator.toscala.extensions.{CogenProduct, ScalaTranslatorExtension}
import com.github.pshirshov.izumi.idealingua.translator.toscala.tools.ScalaMetaTools._
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.ScalaStruct

import scala.meta._


object CirceTranslatorExtension extends ScalaTranslatorExtension {
  private val imports = List(q""" import _root_.io.circe.{Encoder, Decoder} """)
  private val java8Imports = imports ++ List(
    q""" import _root_.io.circe.java8.time._ """
  )


  override def handleIdentifier(ctx: STContext, id: Identifier, product: IdentifierProudct): IdentifierProudct = {
    val boilerplate = withDerived(ctx, id.id)
    product.copy(companion = product.companion.extendDefinition(boilerplate))
  }


  override def handleComposite(ctx: STContext, struct: ScalaStruct, product: CompositeProudct): CompositeProudct = {
    val boilerplate = withDerived(ctx, struct.id)
    product.copy(companion = product.companion.extendDefinition(boilerplate))
  }


  override def handleAdt(ctx: STContext, adt: Adt, product: CogenProduct.AdtProduct): CogenProduct.AdtProduct = {
    val boilerplate = withDerived(ctx, adt.id)
    val elements = product.elements.map {
      e =>
        val id = DTOId(adt.id, e.name)
        val boilerplate = withDerived(ctx, id)
        e.copy(defn = e.defn.extendDefinition(boilerplate))
    }

    product.copy(companion = product.companion.extendDefinition(boilerplate), elements = elements)
  }

  override def handleEnum(ctx: STContext, enum: Enumeration, product: CogenProduct.EnumProduct): CogenProduct.EnumProduct = {
    val boilerplate = withParseable(ctx, enum.id)
    product.copy(companion = product.companion.extendDefinition(boilerplate))
  }

  override def handleInterface(ctx: STContext, interface: Interface, product: InterfaceProduct): InterfaceProduct = {
    val t = ctx.conv.toScala(interface.id)
    val tpe = t.typeFull
    val implementors = ctx.typespace.implementors(interface.id)

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
      q""" implicit val ${Pat.Var(Term.Name(s"encode${interface.id.name}"))}: Encoder[$tpe] = Encoder.instance {
        c => {
          c match {
            ..case $enc
          }
        }
      } """
      ,
      q""" implicit val ${Pat.Var(Term.Name(s"decode${interface.id.name}"))}: Decoder[$tpe] = Decoder.instance(c => {
             val fname = c.keys.flatMap(_.headOption).toSeq.head
             val value = c.downField(fname)
             fname match {
               ..case $dec
             }
           })
       """
    )
    product.copy(companion = product.companion.extendDefinition(circeBoilerplate))
  }


  private def withParseable(ctx: STContext, id: TypeId) = {
    val t = ctx.conv.toScala(id)
    val tpe = t.typeFull
    imports ++ List(
      q""" implicit val ${Pat.Var(Term.Name(s"encode${id.name}"))}: Encoder[$tpe] = Encoder.encodeString.contramap(_.toString) """
      ,
      q""" implicit val ${Pat.Var(Term.Name(s"decode${id.name}"))}: Decoder[$tpe] = Decoder.decodeString.map(${t.termFull}.parse) """
    )
  }

  private def withDerived[T <: Defn](ctx: STContext, id: TypeId) = {
    val tpe = ctx.conv.toScala(id).typeFull
    java8Imports ++ List(
      q""" import _root_.io.circe.generic.semiauto.{deriveDecoder, deriveEncoder} """
      ,
      q""" implicit val ${Pat.Var(Term.Name(s"encode${id.name}"))}: Encoder[$tpe] = deriveEncoder[$tpe] """
      ,
      q""" implicit val ${Pat.Var(Term.Name(s"decode${id.name}"))}: Decoder[$tpe] = deriveDecoder[$tpe] """
    )
  }

  /*
  *   private def withDerived[T <: Defn](ctx: STContext, id: TypeId, defn: T) = {
    val tpe = ctx.conv.toScala(id).typeFull
    val circeBoilerplate = java8Imports ++ List(
      q""" import _root_.io.circe.generic.semiauto.{deriveDecoder, deriveEncoder} """
      ,
      q""" implicit val ${Pat.Var(Term.Name(s"encode${id.name}"))}: Encoder[$tpe] = deriveEncoder[$tpe] """
      ,
      q""" implicit val ${Pat.Var(Term.Name(s"decode${id.name}"))}: Decoder[$tpe] = deriveDecoder[$tpe] """
    )
    val encoder =
      q"""trait ${Type.Name(s"${id.name}Circe")} {
           ..$circeBoilerplate
          }

       """
    defn.extendDefinition(encoder)
  }
  * */
}

