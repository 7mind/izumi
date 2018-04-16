package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.DTOId
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.{Adt, Enumeration, Identifier, Interface}
import com.github.pshirshov.izumi.idealingua.runtime.circe.{CirceWrappedServiceDefinition, MuxedCodec, MuxingCodecProvider}
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext
import com.github.pshirshov.izumi.idealingua.translator.toscala.extensions.ScalaTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.toscala.products.CogenProduct.{CompositeProudct, IdentifierProudct, InterfaceProduct}
import com.github.pshirshov.izumi.idealingua.translator.toscala.products.{CogenProduct, CogenServiceProduct}
import com.github.pshirshov.izumi.idealingua.translator.toscala.tools.ScalaMetaTools._
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.{ScalaStruct, ScalaType, ServiceProduct, runtime}

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


  override def handleService(ctx: STContext, service: ServiceProduct, product: CogenServiceProduct): CogenServiceProduct = {
    val cp = circeRuntimePkg.conv.toScala[MuxingCodecProvider]
    val base = circeRuntimePkg.conv.toScala[CirceWrappedServiceDefinition]
    val codecProvider =
      q""" object CodecProvider extends ${cp.init()} {
        import _root_.io.circe._
        import _root_.io.circe.syntax._

        def requestEncoders: List[PartialFunction[ReqBody, Json]] = List(
               {
                 case ReqBody(v: ${service.serviceInputBase.typeFull}) =>
                   v.asJson
               }
             )

        def responseEncoders: List[PartialFunction[ResBody, Json]] = List(
               {
                 case ResBody(v: ${service.serviceOutputBase.typeFull}) =>
                   v.asJson
               }
             )

        def requestDecoders: List[PartialFunction[CursorForMethod, Result[ReqBody]]] = List(
               {
                 case CursorForMethod(m, packet) if m.service == serviceId =>
                   packet.as[${service.serviceInputBase.typeFull}].map(v => ReqBody(v))
               }
             )

        def responseDecoders: List[PartialFunction[CursorForMethod, Result[ResBody]]] = List(
               {
                 case CursorForMethod(m, packet) if m.service == serviceId =>
                   packet.as[${service.serviceOutputBase.typeFull}].map(v => ResBody(v))
               }
             )
      }"""

    import ctx.conv._
    val derInput = withDerived(service.serviceInputBase)
    val derOutput = withDerived(service.serviceOutputBase)
    val initInput = service.svcBaseTpe.within(derInput.name).init()
    val initOutput = service.svcBaseTpe.within(derOutput.name).init()


    val extensions: List[Stat] = List(q"override def codecProvider: ${cp.typeFull} = CodecProvider", codecProvider)
    val extendedIO = List(derInput.defn, derOutput.defn)

    // TODO: lenses?
    product.copy(
      wrapped = product.wrapped.copy(
        companion = product.wrapped.companion.appendDefinitions(extensions).appendBase(base.init())
      )

      , imports = product.imports :+ runtime.Import.of(circeRuntimePkg)
      , defs = product.defs.copy(
        in = product.defs.in.copy(companion = product.defs.in.companion.appendBase(initInput))
        , out = product.defs.out.copy(companion = product.defs.out.companion.appendBase(initOutput))
        , defs = product.defs.defs.prependDefnitions(extendedIO)
      )
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


  private def withDerived(ctx: STContext, id: TypeId): CirceTrait = {
    val tpe = ctx.conv.toScala(id)
    withDerived(tpe)
  }

  private def withDerived(stype: ScalaType): CirceTrait = {
    val name = stype.fullJavaType.name
    val tpe = stype.typeFull
    CirceTrait(
      s"${name}Circe",
      q"""trait ${Type.Name(s"${name}Circe")} extends _root_.io.circe.java8.time.TimeInstances {
            import _root_.io.circe.{Encoder, Decoder}
            import _root_.io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
            implicit val ${Pat.Var(Term.Name(s"encode$name"))}: Encoder[$tpe] = deriveEncoder[$tpe]
            implicit val ${Pat.Var(Term.Name(s"decode$name"))}: Decoder[$tpe] = deriveDecoder[$tpe]
          }
      """)
  }
}

