package com.github.pshirshov.izumi.idealingua.translator.toscala.extensions

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.DefMethod
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.{Adt, Enumeration, Identifier, Interface}
import com.github.pshirshov.izumi.idealingua.runtime.circe.IRTTimeInstances
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext
import com.github.pshirshov.izumi.idealingua.translator.toscala.products.CogenProduct
import com.github.pshirshov.izumi.idealingua.translator.toscala.products.CogenProduct.{CogenServiceProduct, CompositeProduct, IdentifierProudct, InterfaceProduct}
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.{ClassSource, FullServiceContext, StructContext, runtime}

import scala.meta._

trait CirceTranslatorExtensionBase extends ScalaTranslatorExtension {

  import com.github.pshirshov.izumi.idealingua.translator.toscala.tools.ScalaMetaTools._

  protected case class CirceTrait(name: String, defn: Defn.Trait)

  protected def classDeriverImports: List[Import]


  private val circeRuntimePkg = runtime.Pkg.of[IRTTimeInstances]

  override def handleIdentifier(ctx: STContext, id: Identifier, product: IdentifierProudct): IdentifierProudct = {
    import ctx.conv._
    val boilerplate = withParseable(ctx, id.id)
    val init = toScala(id.id).sibling(boilerplate.name).init()
    product.copy(companionBase = product.companionBase.prependBase(init), more = product.more :+ boilerplate.defn)
  }

  override def handleComposite(ctx: STContext, struct: StructContext, product: CompositeProduct): CompositeProduct = {
    import ctx.conv._
    val boilerplate = withDerivedClass(ctx, struct)
    val init = toScala(struct.struct.id).sibling(boilerplate.name).init()
    product.copy(companionBase = product.companionBase.prependBase(init), more = product.more :+ boilerplate.defn)
  }

  override def handleAdt(ctx: STContext, adt: Adt, product: CogenProduct.AdtProduct): CogenProduct.AdtProduct = {
    val id: TypeId = adt.id
    val implementors = adt.alternatives //.map(m => m.typeId) //ctx.typespace.inheritance.implementingDtos(id)

    import ctx.conv._
    val t = toScala(id)
    val tpe = t.typeFull


    val enc = implementors.map {
      c =>
        p"""case v: ${t.within(c.name).typeFull} => Map(${Lit.String(c.name)} -> v.value).asJson"""

    }


    val dec = implementors.map {
      c =>
        p"""case ${Lit.String(c.name)} => value.as[${toScala(c.typeId).typeAbsolute}].map(${t.within(c.name).termFull}.apply)"""
    }

    val missingDefinitionCase =
      p"""case _ =>
           val cname = ${Lit.String(id.wireId)}
           val alts = List(..${implementors.map(c => Lit.String(c.name))}).mkString(",")
           Left(DecodingFailure(s"Can't decode type $$fname as $$cname, expected one of [$$alts]", value.history))
      """

    val decCases = dec :+ missingDefinitionCase

    val boilerplate = CirceTrait(
      s"${id.name}Circe",
      q"""trait ${Type.Name(s"${id.name}Circe")} {
             import _root_.io.circe.syntax._
             import _root_.io.circe.{Encoder, Decoder, DecodingFailure}

             implicit val ${Pat.Var(Term.Name(s"encode${id.name}"))}: Encoder[$tpe] = Encoder.instance {
                 ..case $enc
             }

             implicit val ${Pat.Var(Term.Name(s"decode${id.name}"))}: Decoder[$tpe] = Decoder.instance(c => {
                 val maybeContent = c.keys.flatMap(_.headOption)
                      .toRight(DecodingFailure("No type name found in JSON, expected JSON of form { \"type_name\": { ...fields } }", c.history))

                 for {
                   fname <- maybeContent
                   value = c.downField(fname)
                   result <- fname match { ..case $decCases }
                 } yield {
                   result
                 }
               }
             )
          }
      """)
    val init = toScala(id).sibling(boilerplate.name).init()
    product.copy(companionBase = product.companionBase.prependBase(init), more = product.more :+ boilerplate.defn)
  }

  override def handleEnum(ctx: STContext, enum: Enumeration, product: CogenProduct.EnumProduct): CogenProduct.EnumProduct = {
    import ctx.conv._
    val boilerplate = withParseable(ctx, enum.id)
    val init = toScala(enum.id).sibling(boilerplate.name).init()
    product.copy(companionBase = product.companionBase.prependBase(init), more = product.more :+ boilerplate.defn)
  }

  override def handleInterface(ctx: STContext, interface: Interface, product: InterfaceProduct): InterfaceProduct = {
    import ctx.conv._
    val t = toScala(interface.id)
    val tpe = t.typeFull
    val implementors = ctx.typespace.inheritance.implementingDtos(interface.id)

    val enc = implementors.map {
      c =>
        p"""case v: ${toScala(c).typeFull} => Map(${Lit.String(c.wireId)} -> v).asJson"""

    }


    val dec = implementors.map {
      c =>
        p"""case ${Lit.String(c.wireId)} => value.as[${toScala(c).typeFull}]"""
    }

    val missingDefinitionCase =
      p"""case _ =>
           val cname = ${Lit.String(interface.id.wireId)}
           val alts = List(..${implementors.map(c => Lit.String(c.wireId))}).mkString(",")
           Left(DecodingFailure(s"Can't decode type $$fname as $$cname, expected one of [$$alts]", value.history))
      """

    val decCases = dec :+ missingDefinitionCase

    val boilerplate = CirceTrait(
      s"${interface.id.name}Circe",
      q"""trait ${Type.Name(s"${interface.id.name}Circe")} {
             import _root_.io.circe.syntax._
             import _root_.io.circe.{Encoder, Decoder, DecodingFailure}

             implicit val ${Pat.Var(Term.Name(s"encode${interface.id.name}"))}: Encoder[$tpe] = Encoder.instance {
               ..case $enc
             }

             implicit val ${Pat.Var(Term.Name(s"decode${interface.id.name}"))}: Decoder[$tpe] = Decoder.instance(c => {
                 val maybeContent = c.keys.flatMap(_.headOption)
                      .toRight(DecodingFailure("No type name found in JSON, expected JSON of form { \"type_name\": { ...fields } }", c.history))

                 for {
                   fname <- maybeContent
                   value = c.downField(fname)
                   result <- fname match { ..case $decCases }
                 } yield result
               }
             )
          }
      """)
    val init = toScala(interface.id).sibling(boilerplate.name).init()
    product.copy(companionBase = product.companionBase.prependBase(init), more = product.more :+ boilerplate.defn)
  }

  override def handleService(ctx: STContext, sCtx: FullServiceContext, product: CogenServiceProduct): CogenServiceProduct = {
    super.handleService(ctx, sCtx, product)
  }

  protected def withParseable(ctx: STContext, id: TypeId): CirceTrait = {
    val t = ctx.conv.toScala(id)
    val tpe = t.typeFull

    CirceTrait(
      s"${id.name}Circe",
      q"""trait ${Type.Name(s"${id.name}Circe")} {
            import _root_.io.circe.{Encoder, Decoder, KeyEncoder, KeyDecoder}
            implicit val ${Pat.Var(Term.Name(s"encode${id.name}"))}: Encoder[$tpe] = Encoder.encodeString.contramap(_.toString)
            implicit val ${Pat.Var(Term.Name(s"decode${id.name}"))}: Decoder[$tpe] = Decoder.decodeString.map(${t.termFull}.parse)
            implicit val ${Pat.Var(Term.Name(s"encodeKey${id.name}"))}: KeyEncoder[$tpe] = KeyEncoder.encodeKeyString.contramap(_.toString)
            implicit val ${Pat.Var(Term.Name(s"decodeKey${id.name}"))}: KeyDecoder[$tpe] = KeyDecoder.decodeKeyString.map(${t.termFull}.parse)
          }
      """)
  }

  protected def withDerivedClass(ctx: STContext, sc: StructContext): CirceTrait = {
    val id = sc.struct.id
    val stype = ctx.conv.toScala(id)
    val struct = ctx.typespace.structure.structure(id)
    val name = stype.fullJavaType.name
    val tpe = stype.typeName

    val unwrap = sc.source match {
      case ClassSource.CsMethodOutput(_, smp) =>
        smp.method.signature.output match {
          case _: DefMethod.Output.Singular =>
            true
          case _ =>
            false
        }
      case _ =>
        false
    }

    val base = Init(circeRuntimePkg.conv.toScala[IRTTimeInstances].typeAbsolute, Name.Anonymous(), List.empty)

    if (unwrap) {
      val singleField = struct.all.head.field
      val ftpe = ctx.conv.toScala(singleField.typeId)

      CirceTrait(
        s"${name}Circe",
        q"""trait ${Type.Name(s"${name}Circe")} extends $base {
            import _root_.io.circe._
            import _root_.io.circe.syntax._

            implicit val ${Pat.Var(Term.Name(s"encode$name"))}: Encoder[$tpe] = Encoder.instance {
              v =>
                v.${Term.Name(singleField.name)}.asJson
            }

            implicit val ${Pat.Var(Term.Name(s"decode$name"))}: Decoder[$tpe] = Decoder.instance {
              v => v.as[${ftpe.typeFull}].map(d => ${stype.termName}(d))
            }
          }
      """)
    } else {
      CirceTrait(
        s"${name}Circe",
        q"""trait ${Type.Name(s"${name}Circe")} extends $base {
            ..$classDeriverImports
            import _root_.io.circe.{Encoder, Decoder}
            implicit val ${Pat.Var(Term.Name(s"encode$name"))}: Encoder[$tpe] = deriveEncoder[$tpe]
            implicit val ${Pat.Var(Term.Name(s"decode$name"))}: Decoder[$tpe] = deriveDecoder[$tpe]
          }
      """)
    }
  }


}

/**
  * Doesn't support sealed traits hierarchies
  */
object CirceDerivationTranslatorExtension extends CirceTranslatorExtensionBase {
  override protected val classDeriverImports: List[Import] = List(
    q""" import _root_.io.circe.derivation.{deriveDecoder, deriveEncoder} """
  )
}
