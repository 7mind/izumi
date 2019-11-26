package izumi.idealingua.translator.toscala

import izumi.idealingua.model.il.ast.typed.TypeDef._
import izumi.idealingua.model.il.ast.typed.{Buzzer, Service, TypeDef}
import izumi.idealingua.model.output.Module
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.translator.CompilerOptions._
import izumi.idealingua.translator.toscala.extensions._
import izumi.idealingua.translator.toscala.products._
import izumi.idealingua.translator.toscala.types.ClassSource
import izumi.idealingua.translator.{Translated, Translator}

import scala.meta._


object ScalaTranslator {
  final val defaultExtensions = Seq(
    AnyvalExtension,
    CastSimilarExtension,
    CastDownExpandExtension,
    CastUpExtension,
    CirceDerivationTranslatorExtension,
  )
}



class ScalaTranslator(ts: Typespace, options: ScalaTranslatorOptions)
  extends Translator {
  protected val ctx: STContext = new STContext(ts, options.extensions)



  def translate(): Translated = {
    import izumi.fundamentals.collections.IzCollections._
    val aliases = ctx.typespace.domain.types
      .collect {
        case a: Alias =>
          ctx.modules.toModuleId(a) -> renderAlias(a)
      }
      .toMultimap
      .mapValues(_.flatten.toSeq)

    val packageObjects = aliases.map {
      case (id, content) =>
        val pkgName = id.name.split('.').head

        val code =
          s"""
             |package object $pkgName {
             |${content.map(_.toString()).mkString("\n\n")}
             |}
           """.stripMargin
        Module(id.copy(name = "package-object.scala"), ctx.modules.withPackage(id.path.init, code))
    }

    val modules = Seq(
      ctx.typespace.domain.types.flatMap(translateDef)
      , ctx.typespace.domain.services.flatMap(translateService)
      , ctx.typespace.domain.buzzers.flatMap(translateBuzzer)
      , packageObjects
    ).flatten

    Translated(ts, ctx.ext.extend(modules))
  }

  protected def translateBuzzer(definition: Buzzer): Seq[Module] = {
    ctx.modules.toSource(
      definition.id.domain
      , ctx.modules.toModuleId(definition.id)
      , ctx.serviceRenderer.renderService(definition.asService)
    )
  }

  protected def translateService(definition: Service): Seq[Module] = {
    ctx.modules.toSource(
      definition.id.domain
      , ctx.modules.toModuleId(definition.id)
      , ctx.serviceRenderer.renderService(definition)
    )
  }

  protected def translateDef(definition: TypeDef): Seq[Module] = {
    val defns = definition match {
      case i: Enumeration =>
        ctx.enumRenderer.renderEnumeration(i)
      case i: Identifier =>
        ctx.idRenderer.renderIdentifier(i)
      case i: Interface =>
        ctx.interfaceRenderer.renderInterface(i)
      case d: Adt =>
        ctx.adtRenderer.renderAdt(d)
      case d: DTO =>
        ctx.compositeRenderer.defns(ctx.tools.mkStructure(d.id), ClassSource.CsDTO(d))
      case _: Alias =>
        RenderableCogenProduct.empty
    }

    ctx.modules.toSource(definition.id.path.domain, ctx.modules.toModuleId(definition), defns)
  }

  protected def renderAlias(i: Alias): Seq[Defn] = {
    Seq(q"type ${ctx.conv.toScala(i.id).typeName} = ${ctx.conv.toScala(i.target).typeFull}")
  }




}

