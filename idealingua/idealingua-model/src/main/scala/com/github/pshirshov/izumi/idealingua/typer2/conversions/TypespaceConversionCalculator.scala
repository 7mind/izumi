package com.github.pshirshov.izumi.idealingua.typer2.conversions

import com.github.pshirshov.izumi.idealingua.typer2.WarnLogger
import com.github.pshirshov.izumi.idealingua.typer2.model.Conversion.model.ConversionDirection
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.IzStructure
import com.github.pshirshov.izumi.idealingua.typer2.model._

import scala.collection.mutable.ArrayBuffer

class TypespaceConversionCalculator(ts2: Typespace2) extends WarnLogger {

  import TypespaceConversionCalculator._

  private def warnings = new ArrayBuffer[T2Warn]()

  override def log(w: T2Warn): Unit = {
    warnings.append(w)
  }

  def findAllConversions(): Output = {
    // TODO: automatic conversions aren't good for us, see https://github.com/pshirshov/izumi-r2/issues/504
    val calc = new ConversionCalculator(this, ts2)
    val structures = ts2.types.map(_.member).collect({ case s: IzStructure => s })
    val ignoredParents = Builtins.mappingSpecials.keys.toSet[IzTypeId]
    import com.github.pshirshov.izumi.idealingua.typer2.indexing.TypespaceTools._

    val upAndDown = structures.flatMap {
      s =>
        s.allParents.diff(ignoredParents).flatMap {
          p =>
            val ps = ts2.asStructureUnsafe(p)
            val upcasts = calc.conversion(s, ps, ConversionDirection.Upcast, withHeuristic = true)
            val downcasts = calc.conversion(ps, s, ConversionDirection.Downcast, withHeuristic = true)
            upcasts ++ downcasts
        }
    }

    val structural = structures.flatMap {
      s =>
        s.allStructuralParents.diff(ignoredParents).flatMap {
          p =>
            val ps = ts2.asStructureUnsafe(p)
            val upcasts = calc.conversion(s, ps, ConversionDirection.StructuralUpcast, withHeuristic = true)
            val downcasts = calc.conversion(ps, s, ConversionDirection.StructuralDowncast, withHeuristic = true)
            upcasts ++ downcasts
        }
    }

    val allSChildren = ts2.inheritance.allStructuralChildren()
    val structuralSiblings = structures.flatMap {
      s =>
        val children = allSChildren.get(s.id).toSet.flatten.diff(ignoredParents)

        children.flatMap {
          sibling1 =>
            (children - sibling1).flatMap {
              sibling2 =>
                calc.conversion(ts2.asStructureUnsafe(sibling1), ts2.asStructureUnsafe(sibling2), ConversionDirection.StructuralUpcast, withHeuristic = false)

            }
        }
    }

    Output(upAndDown ++ structural ++ structuralSiblings, warnings.toList)
  }
}

object TypespaceConversionCalculator {

  final case class Output(conversions: List[Conversion], warnings: List[T2Warn])

}
