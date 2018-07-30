package com.github.pshirshov.izumi.idealingua.il.parser.model

import com.github.pshirshov.izumi.idealingua.model.common.{IndefiniteMixin, TypeId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.RawField

sealed trait StructOp

object StructOp {

  final case class Extend(tpe: TypeId.InterfaceId) extends StructOp

  final case class Mix(tpe: IndefiniteMixin) extends StructOp

  final case class Drop(tpe: IndefiniteMixin) extends StructOp

  final case class AddField(field: RawField) extends StructOp

  final case class RemoveField(field: RawField) extends StructOp

}
