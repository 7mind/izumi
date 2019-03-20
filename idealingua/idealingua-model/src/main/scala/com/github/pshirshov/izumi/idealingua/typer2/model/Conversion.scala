package com.github.pshirshov.izumi.idealingua.typer2.model

import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.BasicField


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
