package com.github.pshirshov.izumi.idealingua.typer2.conversions

import com.github.pshirshov.izumi.idealingua.typer2.WarnLogger
import com.github.pshirshov.izumi.idealingua.typer2.model.Conversion.model.ConversionDirection
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.IzStructure
import com.github.pshirshov.izumi.idealingua.typer2.model._

import scala.collection.mutable.ArrayBuffer

class TypespaceConversionCalculator(ts2: Typespace2) extends WarnLogger {

  import TypespaceConversionCalculator._
  import com.github.pshirshov.izumi.idealingua.typer2.indexing.TypespaceTools._
  import com.github.pshirshov.izumi.functional.IzEither._

  private def warnings = new ArrayBuffer[T2Warn]()

  override def log(w: T2Warn): Unit = {
    warnings.append(w)
  }

  def findAllConversions(): Either[List[T2Fail], Output] = {
    val structures = ts2.types.map(_.member).collect({ case s: IzStructure => s })
    val ignoredParents = Builtins.mappingSpecials.keys.toSet[IzTypeId]

    val upAndDown = structures.flatMap {
      s =>
        s.allParents.diff(ignoredParents).flatMap {
          p =>
            val upcasts = ConversionSpec(s.id, p, ConversionDirection.Upcast, withHeuristic = true)
            val downcasts = ConversionSpec(p, s.id, ConversionDirection.Downcast, withHeuristic = true)
            Seq(upcasts, downcasts)
        }
    }

    val structural = structures.flatMap {
      s =>
        s.allStructuralParents.diff(ignoredParents).flatMap {
          p =>
            val upcasts = ConversionSpec(s.id, p, ConversionDirection.StructuralUpcast, withHeuristic = true)
            val downcasts = ConversionSpec(p, s.id, ConversionDirection.StructuralDowncast, withHeuristic = true)
            Seq(upcasts, downcasts)
        }
    }

    val allSChildren = ts2.inheritance.allStructuralChildren()
    val structuralSiblings = structures.flatMap {
      s =>
        val children = allSChildren.get(s.id).toSet.flatten.diff(ignoredParents)

        children.flatMap {
          sibling1 =>
            (children - sibling1).map {
              sibling2 =>
                ConversionSpec(sibling1, sibling2, ConversionDirection.StructuralUpcast, withHeuristic = false)

            }
        }
    }

    val allSpecs = upAndDown ++ structural ++ structuralSiblings
    // TODO: automatic conversions aren't good for us, we should support custom specs, see https://github.com/pshirshov/izumi-r2/issues/504

    compute(allSpecs)
  }

  private def compute(allSpecs: List[ConversionSpec]): Either[List[T2Fail], Output] = {
    val calc = new ConversionCalculator(this, ts2)
    val all = allSpecs.map {
      spec =>
        for {
          from <- ts2.asStructure(spec.from)
          to <- ts2.asStructure(spec.to)
        } yield {
          calc.makeConversions(from, to, spec.direction, spec.withHeuristic)
        }

    }.biFlatAggregate

    for {
      a <- all
    } yield {
      Output(a, warnings.toList)
    }
  }
}

object TypespaceConversionCalculator {

  final case class Output(conversions: List[Conversion], warnings: List[T2Warn])

  final case class ConversionSpec(from: IzTypeId, to: IzTypeId, direction: ConversionDirection, withHeuristic: Boolean)

}
