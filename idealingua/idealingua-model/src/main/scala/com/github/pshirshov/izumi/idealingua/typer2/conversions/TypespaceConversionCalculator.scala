package com.github.pshirshov.izumi.idealingua.typer2.conversions

import com.github.pshirshov.izumi.idealingua.typer2.WarnLogger
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
    val calc = new ConversionCalculator(this, ts2)
    val structures = ts2.types.map(_.member).collect({case s: IzStructure => s})
    val ignoredParents = Builtins.mappingSpecials.keys.toSet[IzTypeId]
    import com.github.pshirshov.izumi.idealingua.typer2.model.TypespaceTools._

    val upAndDown = structures.flatMap {
      s =>
        s.parents.filterNot(p => ignoredParents.contains(p)).flatMap {
          p =>
            val ps = ts2.asStructureUnsafe(p)
            val upcasts = calc.conversion(s, ps)
            val downcasts = calc.conversion(ps, s)
            upcasts ++ downcasts
        }
    }

    Output(upAndDown, warnings.toList)
  }
}

object TypespaceConversionCalculator {

  final case class Output(conversions: List[Conversion], warnings: List[T2Warn])

}
