package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.DTOId
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.{Adt, Enumeration, Identifier, Interface}
import com.github.pshirshov.izumi.idealingua.runtime.circe.{CirceWrappedServiceDefinition, MuxedCodec, MuxingCodecProvider}
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext
import com.github.pshirshov.izumi.idealingua.translator.toscala.products.CogenProduct.{CompositeProudct, IdentifierProudct, InterfaceProduct}
import com.github.pshirshov.izumi.idealingua.translator.toscala.extensions.ScalaTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.toscala.products.{CogenProduct, CogenServiceProduct}
import com.github.pshirshov.izumi.idealingua.translator.toscala.tools.ScalaMetaTools._
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.ScalaStruct
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.runtime

import scala.meta._


object CirceTranslatorExtension extends ScalaTranslatorExtension {

  private case class CirceTrait(name: String, defn: Defn.Trait)

  private val circeRuntimePkg = runtime.Pkg.of[MuxedCodec]

  override def handleIdentifier(ctx: STContext, id: Identifier, product: IdentifierProudct): IdentifierProudct = {
    import ctx.conv._
    val boilerplate = withParseable(ctx, id.id)
    val init = toScala(id.id).sibling(boilerplate.name).init()
    product.copy(companion = product.companion.prependBase(init), more = product.more :+ boilerplate.defn)
  }


  override def handleComposite(ctx: STContext, struct: ScalaStruct, product: CompositeProudct): CompositeProudct = {
    import ctx.conv._
    val boilerplate = withDerived(ctx, struct.id)
    val init = toScala(struct.id).sibling(boilerplate.name).init()
    product.copy(companion = product.companion.prependBase(init), more = product.more :+ boilerplate.defn)
  }


  override def handleAdt(ctx: STContext, adt: Adt, product: CogenProduct.AdtProduct): CogenProduct.AdtProduct = {
    import ctx.conv._

    val elements = product.elements.map {
      e =>
        val id = DTOId(adt.id, e.name)
        val boilerplate = withDerived(ctx, id)
        val init = toScala(id).sibling(boilerplate.name).init()
        e.copy(companion = e.companion.prependBase(init), more = e.more :+ boilerplate.defn)
    }

    val boilerplate = withDerived(ctx, adt.id)
    val init = toScala(adt.id).sibling(boilerplate.name).init()

    product.copy(companion = product.companion.prependBase(init), more = product.more :+ boilerplate.defn, elements = elements)
  }

  override def handleEnum(ctx: STContext, enum: Enumeration, product: CogenProduct.EnumProduct): CogenProduct.EnumProduct = {
    import ctx.conv._
    val boilerplate = withParseable(ctx, enum.id)
    val init = toScala(enum.id).sibling(boilerplate.name).init()
    product.copy(companion = product.companion.prependBase(init), more = product.more :+ boilerplate.defn)
  }

  override def handleInterface(ctx: STContext, interface: Interface, product: InterfaceProduct): InterfaceProduct = {
    import ctx.conv._
    val t = toScala(interface.id)
    val tpe = t.typeFull
    val implementors = ctx.typespace.inheritance.implementingDtos(interface.id)

    val enc = implementors.map {
      c =>
        p"""case v: ${toScala(c).typeFull} => Map(${Lit.String(str(c))} -> v).asJson"""

    }

    val dec = implementors.map {
      c =>
        p"""case ${Lit.String(str(c))} => value.as[${toScala(c).typeFull}]"""
    }

    val boilerplate = CirceTrait(
      s"${interface.id.name}Circe",
      q"""trait ${Type.Name(s"${interface.id.name}Circe")} {
             import _root_.io.circe.syntax._
             import _root_.io.circe.{Encoder, Decoder}
             implicit val ${Pat.Var(Term.Name(s"encode${interface.id.name}"))}: Encoder[$tpe] = Encoder.instance {
                     c => {
                       c match {
                         ..case $enc
                       }
                     }
                   }
             implicit val ${Pat.Var(Term.Name(s"decode${interface.id.name}"))}: Decoder[$tpe] = Decoder.instance(c => {
                          val fname = c.keys.flatMap(_.headOption).toSeq.head
                          val value = c.downField(fname)
                          fname match {
                            ..case $dec
                          }
                        })
          }
      """)
    val init = toScala(interface.id).sibling(boilerplate.name).init()
    product.copy(companion = product.companion.prependBase(init), more = product.more :+ boilerplate.defn)
  }


  override def handleService(ctx: STContext, service: Service, product: CogenServiceProduct): CogenServiceProduct = {
    val cp = circeRuntimePkg.conv.toScala[MuxingCodecProvider]
    val base = circeRuntimePkg.conv.toScala[CirceWrappedServiceDefinition]
    val codecProvider =
      q""" object CodecProvider extends ${cp.init()} {
        import _root_.io.circe._
        import _root_.io.circe.syntax._

        override def requestEncoders: List[PartialFunction[ReqBody, Json]] = ???

        override def responseEncoders: List[PartialFunction[ResBody, Json]] = ???

        override def requestDecoders: List[PartialFunction[CursorForMethod, Result[ReqBody]]] = ???

        override def responseDecoders: List[PartialFunction[CursorForMethod, Result[ResBody]]] = ???
      }"""
    val extensions = List(q"override def codecProvider: ${cp.typeFull} = CodecProvider", codecProvider)

    product.copy(
      wrapped = product.wrapped.copy(companion = product.wrapped.companion.extendDefinition(extensions).appendBase(base.init()))
      , imports = product.imports :+ runtime.Import.of(circeRuntimePkg)
    )
  }

  private def str(c: DTOId) = {
    s"${c.pkg.mkString(".")}#${c.name}"
  }

  private def withParseable(ctx: STContext, id: TypeId) = {
    val t = ctx.conv.toScala(id)
    val tpe = t.typeFull


    CirceTrait(
      s"${id.name}Circe",
      q"""trait ${Type.Name(s"${id.name}Circe")} {
            import _root_.io.circe.{Encoder, Decoder}
            implicit val ${Pat.Var(Term.Name(s"encode${id.name}"))}: Encoder[$tpe] = Encoder.encodeString.contramap(_.toString)
            implicit val ${Pat.Var(Term.Name(s"decode${id.name}"))}: Decoder[$tpe] = Decoder.decodeString.map(${t.termFull}.parse)
          }
      """)
  }


  private def withDerived[T <: Defn](ctx: STContext, id: TypeId) = {
    val tpe = ctx.conv.toScala(id).typeFull


    CirceTrait(
      s"${id.name}Circe",
      q"""trait ${Type.Name(s"${id.name}Circe")} extends _root_.io.circe.java8.time.TimeInstances {
            import _root_.io.circe.{Encoder, Decoder}
            import _root_.io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
            implicit val ${Pat.Var(Term.Name(s"encode${id.name}"))}: Encoder[$tpe] = deriveEncoder[$tpe]
            implicit val ${Pat.Var(Term.Name(s"decode${id.name}"))}: Decoder[$tpe] = deriveDecoder[$tpe]
          }
      """)
  }
}

