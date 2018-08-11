package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{Service, TypeDef}
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.Translator
import com.github.pshirshov.izumi.idealingua.translator.TypespaceCompiler.ScalaTranslatorOptions
import com.github.pshirshov.izumi.idealingua.translator.toscala.extensions._
import com.github.pshirshov.izumi.idealingua.translator.toscala.products.CogenProduct._
import com.github.pshirshov.izumi.idealingua.translator.toscala.products._

import scala.meta._


object ScalaTranslator {
  final val defaultExtensions = Seq(
    CastSimilarExtension
    , CastDownExpandExtension
    , CastUpExtension
    , AnyvalExtension
  )
}


class ScalaTranslator(ts: Typespace, options: ScalaTranslatorOptions)
  extends Translator {
  protected val ctx: STContext = new STContext(ts, options.extensions)



  def translate(): Seq[Module] = {
    import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._
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
        Module(id, ctx.modules.withPackage(id.path.init, code))
    }

    val modules = Seq(
      ctx.typespace.domain.types.flatMap(translateDef)
      , ctx.typespace.domain.services.flatMap(translateService)
      , packageObjects
    ).flatten

    addRuntime(options, ctx.ext.extend(modules))
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

