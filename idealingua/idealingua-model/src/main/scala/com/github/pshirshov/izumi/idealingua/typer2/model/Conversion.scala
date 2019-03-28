package com.github.pshirshov.izumi.idealingua.typer2.model

import com.github.pshirshov.izumi.idealingua.typer2.model.Conversion.model.{ConversionDirection, ConversionKind}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.BasicField


sealed trait Conversion {
  def from: IzTypeId

  def to: IzTypeId

  def kind: ConversionKind

  def direction: ConversionDirection
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


    sealed trait ConversionKind

    object ConversionKind {
      case object Heuristic extends ConversionKind
      case object Reliable extends ConversionKind
    }

    sealed trait ConversionDirection

    object ConversionDirection {
      case object Upcast extends ConversionDirection
      case object Downcast extends ConversionDirection
      case object StructuralUpcast extends ConversionDirection
      case object StructuralDowncast extends ConversionDirection
      case object StructuralSibling extends ConversionDirection
    }
  }


  import model._

  final case class Copy(from: IzTypeId, to: IzTypeId, ops: Seq[ConstructionOp.Transfer], kind: ConversionKind, direction: ConversionDirection) extends Conversion

  final case class Expand(from: IzTypeId, to: IzTypeId, ops: Seq[ConstructionOp], kind: ConversionKind, direction: ConversionDirection) extends Conversion

}
