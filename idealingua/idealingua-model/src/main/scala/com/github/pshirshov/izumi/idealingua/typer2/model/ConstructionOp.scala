package com.github.pshirshov.izumi.idealingua.typer2.model

import com.github.pshirshov.izumi.idealingua.typer2.WarnLogger
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.IzStructure
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.BasicField

import scala.collection.immutable.ListSet
import scala.collection.mutable.ArrayBuffer


sealed trait Conversion {
  def from: IzTypeId
  def to: IzTypeId
}

object Conversion {

  object model {
    sealed trait ConstructionOp {
      def target: BasicField
    }

    object ConstructionOp {

      final case class Emerge(required: IzTypeReference, target: BasicField) extends ConstructionOp

      final case class Transfer(required: IzTypeId, source: BasicField, target: BasicField) extends ConstructionOp

    }
  }


  import model._

  final case class Copy(from: IzTypeId, to: IzTypeId, ops: Seq[ConstructionOp.Transfer]) extends Conversion

  final case class Expand(from: IzTypeId, to: IzTypeId, ops: Seq[ConstructionOp]) extends Conversion

}


class TypespaceConversionCalculator(ts2: Typespace2) extends WarnLogger {
  import TypespaceConversionCalculator._

  private def warnings = new ArrayBuffer[T2Warn]()
  override def log(w: T2Warn): Unit = {
    warnings.append(w)
  }

  def findAllConversions(): Output = {
    Output(???, warnings.toList)
  }
}

object TypespaceConversionCalculator {
  final case class Output(conversions: List[Conversion], warnings: List[T2Warn])
}

class ConversionCalculator(warnLogger: WarnLogger, ts2: Typespace2) {
  import Conversion._
  import Conversion.model._

  def conversion(from: IzStructure, to: IzStructure): List[Conversion] = {
    val fromFields = from.fields.map(_.basic).to[ListSet]
    val toFields = to.fields.map(_.basic).to[ListSet]
    val copyable = fromFields.intersect(toFields)
    val toCopy = copyable.map(f => ConstructionOp.Transfer(from.id, f, f)).toSeq
    val missing: Set[BasicField] = toFields.diff(fromFields)

    val directMissingCopy = missing.map(m => ConstructionOp.Emerge(m.ref, m)).toSeq

    val conversion1 = List(make(from.id, to.id, toCopy ++ directMissingCopy))

    val conversion2 = if (missing.nonEmpty) {
      val solutions = findSolutions(to, missing)
      solutions.map(s => make(from.id, to.id, toCopy ++ s))
    } else {
      List.empty
    }

    val result = conversion1
    filterValid(from, to, result)
  }

  private def findSolutions(to: IzStructure, missing: Set[BasicField]): Seq[Seq[ConstructionOp]] = {
    ???
  }

  private def make(from: IzTypeId, to: IzTypeId, ops: Seq[ConstructionOp]): Conversion = {
    val transfers = ops.collect({case f: ConstructionOp.Transfer => f})
    val emerges = ops.collect({case f: ConstructionOp.Emerge => f})

    if (emerges.isEmpty) {
      Copy(from, to, transfers)
    } else {
      Expand(from, to, ops)
    }
  }

  private def filterValid(from: IzStructure, to: IzStructure, result: List[Conversion]): List[Conversion] = {
    val toFields = to.fields.map(_.basic).to[ListSet]
    val (good, bad) = result.partition {
      c =>
        val targetFields = c match {
          case Copy(_, _, ops) =>
            ops.map(_.target)
          case Expand(_, _, ops) =>
            ops.map(_.target)
        }
        targetFields.size == to.fields.size && targetFields.toSet == toFields.toSeq.toSet // ListSet considers order in equals :/
    }

    bad.foreach {
      b =>
        warnLogger.log(T2Warn.FailedConversion(b))
    }

    good
  }

}
