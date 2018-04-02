package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.Field

sealed trait StructOp

object StructOp {

  case class Extend(tpe: TypeId.InterfaceId) extends StructOp

  case class Mix(tpe: TypeId.InterfaceId) extends StructOp

  case class Drop(tpe: TypeId.InterfaceId) extends StructOp

  case class AddField(field: Field) extends StructOp

  case class RemoveField(field: Field) extends StructOp

}
