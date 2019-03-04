package com.github.pshirshov.izumi.idealingua.typer2.model

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawNongenericRef
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.BasicField

sealed trait T2Warn

object T2Warn {
  final case class MissingFieldsToRemove(tpe: IzTypeId, removals: Set[BasicField]) extends T2Warn
  final case class MissingParentsToRemove(tpe: IzTypeId, removals: Set[RawNongenericRef]) extends T2Warn
  final case class MissingBranchesToRemove(tpe: IzTypeId, removals: Set[String]) extends T2Warn
}
