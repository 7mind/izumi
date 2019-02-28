package com.github.pshirshov.izumi.idealingua.typer2.model

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTypeDef
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.{Operation, UnresolvedName}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.{BasicField, FName, FullField}

sealed trait T2Fail

object T2Fail {

  object model {
    final case class FieldConflict(tpe: IzTypeReference, expectedToBeParent: IzTypeReference)
  }
  import model._
  final case class CircularDependenciesDetected(loops: List[Set[UnresolvedName]]) extends T2Fail

  final case class ConflictingNames(conflicts: Set[UnresolvedName]) extends T2Fail

  final case class UnexpectedException(exception: Throwable) extends T2Fail


  sealed trait BuilderFail extends T2Fail
  sealed trait OperationFail extends BuilderFail {
    def context: Operation
  }

  final case class DependencyMissing(context: Operation, missing: Set[UnresolvedName], blocked: UnresolvedName) extends OperationFail
  final case class SingleDeclaredType(context: Operation, issue: RawTypeDef.DeclaredType) extends OperationFail

  final case class ConflictingFields(tpe: IzTypeId, conflicts: Map[FName, Seq[FullField]]) extends BuilderFail

  final case class ParentTypeExpectedToBeStructure(tpe: IzTypeId, problematic: IzTypeId) extends BuilderFail
  final case class ParentCannotBeGeneric(tpe: IzTypeId, problematic: IzTypeReference) extends BuilderFail


  sealed trait VerificationFail extends BuilderFail {
    def tpe: IzTypeId
  }

  final case class ContradictiveFieldDefinition(tpe: IzTypeId, field: FullField, conflicts: Seq[FieldConflict]) extends VerificationFail
}

sealed trait T2Warn

object T2Warn {
  final case class NothingToRemove(tpe: IzTypeId, removals: Set[BasicField]) extends T2Warn
}
