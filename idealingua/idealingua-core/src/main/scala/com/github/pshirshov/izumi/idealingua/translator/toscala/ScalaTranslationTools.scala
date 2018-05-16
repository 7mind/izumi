package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.common.{SigParam, StructureId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.typespace.structures.ConverterDef
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.CompositeStructure

import scala.meta._


class ScalaTranslationTools(ctx: STContext) {
  import ctx.conv._
  import com.github.pshirshov.izumi.idealingua.translator.toscala.types.ScalaField._

  def mkStructure(id: StructureId): CompositeStructure = {
    val fields = ctx.typespace.structure.structure(id).toScala
    new CompositeStructure(ctx, fields)
  }


  def idToParaName(id: TypeId): Term.Name = Term.Name(ctx.typespace.tools.idToParaName(id))

  private def toAssignment(f: SigParam): Term.Assign = {
    f.sourceFieldName match {
      case Some(sourceFieldName) =>
        q""" ${Term.Name(f.targetFieldName)} = ${Term.Name(f.source.sourceName)}.${Term.Name(sourceFieldName)}  """

      case None =>
        q""" ${Term.Name(f.targetFieldName)} = ${Term.Name(f.source.sourceName)}  """

    }
  }

  def makeParams(t: ConverterDef): List[Term.Param] = {
    t.outerParams
      .map(f => (Term.Name(f.sourceName), ctx.conv.toScala(f.sourceType).typeFull))
      .toParams
  }

  def makeConstructor(t: ConverterDef): List[Term.Assign] = {
    t.allFields.map(ctx.tools.toAssignment)
  }


}
