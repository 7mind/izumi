package com.github.pshirshov.izumi.idealingua.typer2.model

import com.github.pshirshov.izumi.idealingua.model.il.ast.InputPosition
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{InterpContext, RawTypeDef}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains.Import
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.{RawDeclaredTypeName, RawRef}
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.{Operation, TypenameRef}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.{FName, FullField, NodeMeta}

sealed trait T2Fail

object T2Fail {
  sealed trait WithMeta {
    def meta: NodeMeta
  }

  object model {
    final case class FieldConflict(tpe: IzTypeReference, expectedToBeParent: IzTypeReference)
  }
  import model._
  final case class CircularDependenciesDetected(loops: List[Set[TypenameRef]]) extends T2Fail

  final case class NameConflict(problem: TypenameRef) extends T2Fail
  final case class ConflictingImports(conflicts: Map[String, Set[Import]]) extends T2Fail

  final case class UnexpectedException(exception: Throwable) extends T2Fail

  final case class TopLevelNameConflict(kind: String, conflicts: Map[RawDeclaredTypeName, Seq[InputPosition]]) extends T2Fail

  sealed trait BuilderFail extends T2Fail

  sealed trait OperationFail extends T2Fail with BuilderFail {
    def context: Operation
  }

  sealed trait BuilderFailWithMeta extends BuilderFail

  final case class DependencyMissing(context: Operation, missing: Set[TypenameRef], blocked: TypenameRef) extends OperationFail
  //final case class SingleDeclaredType(context: Operation, issue: RawTypeDef.DeclaredType, meta: NodeMeta) extends OperationFail with WithMeta

  final case class ConflictingFields(tpe: IzTypeId, conflicts: Map[FName, Seq[FullField]], meta: NodeMeta) extends BuilderFailWithMeta 

  final case class ParentTypeExpectedToBeStructure(tpe: IzTypeId, problematic: IzTypeId, meta: NodeMeta) extends BuilderFailWithMeta
  final case class ParentCannotBeGeneric(tpe: IzTypeId, problematic: IzTypeReference, meta: NodeMeta) extends BuilderFailWithMeta

  final case class CannotApplyTypeModifiers(tpe: IzTypeId, problematic: IzTypeId, meta: NodeMeta) extends BuilderFailWithMeta

  final case class EnumExpected(tpe: IzTypeId, problematic: IzTypeId, meta: NodeMeta) extends BuilderFailWithMeta
  final case class EnumExpectedButGotGeneric(tpe: IzTypeId, problematic: IzTypeReference, meta: NodeMeta) extends BuilderFailWithMeta

  final case class UnexpectedArguments(context: IzTypeId, problems: Seq[InterpContext], meta: NodeMeta) extends BuilderFailWithMeta
  final case class UnexpectedAdtCloneModifiers(context: IzTypeId, meta: NodeMeta) extends BuilderFailWithMeta
  final case class UnexpectedStructureCloneModifiers(context: IzTypeId, meta: NodeMeta) extends BuilderFailWithMeta
  final case class FeatureUnsupported(context: IzTypeId, explanation: String, meta: NodeMeta) extends BuilderFailWithMeta
  final case class GenericAdtBranchMustBeNamed(context: IzTypeId, problematic: RawRef, meta: NodeMeta) extends BuilderFailWithMeta

  sealed trait VerificationFail extends BuilderFail

  final case class ContradictiveFieldDefinition(tpe: IzTypeId, field: FullField, conflicts: Seq[FieldConflict], meta: NodeMeta) extends VerificationFail
  final case class MissingTypespaceMembers(missingRefs: Map[IzTypeId, Set[IzTypeId]]) extends VerificationFail
}




