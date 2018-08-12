package com.github.pshirshov.izumi.idealingua.translator.toscala.tools

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.DTOId
import com.github.pshirshov.izumi.idealingua.model.common.{Builtin, SigParam, StructureId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.typespace.structures.ConverterDef
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.CompositeStructure

import scala.meta._

case class Params(params: List[Term.Param], types: List[TypeId], assertions: List[Term.ApplyInfix]) {
  def assertion: List[Term] = {
    if (assertions.isEmpty) {
      List.empty
    } else {
      val expr = assertions.tail.foldLeft(assertions.head) {
        case (a, acc) =>
          q"$acc && $a"
      }
      List(q"assert($expr)")
    }
  }
}

class ScalaTranslationTools(ctx: STContext) {

  import com.github.pshirshov.izumi.idealingua.translator.toscala.types.ScalaField._
  import ctx.conv._

  def mkStructure(id: StructureId): CompositeStructure = {
    val fields = ctx.typespace.structure.structure(id).toScala
    new CompositeStructure(ctx, fields)
  }


  def idToParaName(id: TypeId): Term.Name = Term.Name(ctx.typespace.tools.idToParaName(id))

  def makeParams(t: ConverterDef): Params = {
    val out = t.outerParams
      .map {
        f =>
          /*
          ANYVAL:ERASURE
           this is a workaround for anyval/scala erasure issue.
           We prohibit to use DTOs directly in parameters and using mirrors instead
            */
          val source = f.sourceType match {
            case s: DTOId =>
              ctx.typespace.defnId(s)

            case o =>
              o
          }

          val scalaType = ctx.conv.toScala(source)
          val name = Term.Name(f.sourceName)

          (f, source, (name, scalaType.typeFull))
      }

    // this allows us to get rid of "unused" warnings and do a good thing
    val assertions = out.map {
      case (field, _, (name, _)) =>
        if (!ctx.typespace.dealias(field.sourceType).isInstanceOf[Builtin]) {
          List(q"$name != null")
        } else {
          List.empty
        }
    }

    Params(out.map(_._3).toParams, out.map(_._2), assertions.flatten)
  }

  def makeConstructor(t: ConverterDef): List[Term.Assign] = {
    t.allFields.map(toAssignment)
  }

  private def toAssignment(f: SigParam): Term.Assign = {
    f.sourceFieldName match {
      case Some(sourceFieldName) =>
        q""" ${Term.Name(f.targetFieldName)} = ${Term.Name(f.source.sourceName)}.${Term.Name(sourceFieldName)}  """


      case None =>
        val sourceType = f.source.sourceType

        val defnid = sourceType match {
          case d: DTOId =>
            ctx.typespace.tools.defnId(d)
          case o =>
            o
        }

        if (defnid == sourceType) {
          q""" ${Term.Name(f.targetFieldName)} = ${Term.Name(f.source.sourceName)}  """
        } else {
          q""" ${Term.Name(f.targetFieldName)} = ${ctx.conv.toScala(sourceType).termFull}(${Term.Name(f.source.sourceName)})"""
        }
    }
  }


}
