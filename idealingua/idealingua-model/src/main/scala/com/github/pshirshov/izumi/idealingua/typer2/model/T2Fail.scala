package com.github.pshirshov.izumi.idealingua.typer2.model

import com.github.pshirshov.izumi.idealingua.model.il.ast.InputPosition
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{InterpContext, RawConstMeta}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.{RawDeclaredTypeName, RawRef}
import com.github.pshirshov.izumi.idealingua.typer2.GoodImport
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.{Operation, TypenameRef}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model._
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.IzName
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.{IzTypeArgName, RefToTLTLink}

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
  final case class MissingType(problem: TypenameRef, locations: Map[TypenameRef, Seq[InputPosition]]) extends T2Fail
  final case class ConflictingImports(conflicts: Map[String, Set[GoodImport]]) extends T2Fail


  final case class TopLevelNameConflict(kind: String, conflicts: Map[RawDeclaredTypeName, Seq[InputPosition]]) extends T2Fail

  sealed trait BuilderFail extends T2Fail

  final case class UnexpectedException(exception: Throwable) extends BuilderFail

  sealed trait OperationFail extends T2Fail with BuilderFail {
    def context: Operation
  }

  sealed trait BuilderFailWithMeta extends BuilderFail

  final case class TypesAlreadyRegistered(tpe: Set[IzName]) extends BuilderFail

  final case class DependencyMissing(context: Operation, missing: Set[TypenameRef], blocked: TypenameRef) extends OperationFail

  final case class ConflictingFields(tpe: IzTypeId, conflicts: Map[FName, Seq[FullField]], meta: NodeMeta) extends BuilderFailWithMeta 

  final case class ErrorMarkerCannotBeUsedAsConcept(tpe: IzTypeId, problematic: IzTypeId, meta: NodeMeta) extends BuilderFailWithMeta
  final case class ParentTypeExpectedToBeStructure(tpe: IzTypeId, problematic: IzTypeId, meta: NodeMeta) extends BuilderFailWithMeta
  final case class ParentTypeExpectedToBeScalar(tpe: IzTypeId, problematic: IzTypeId, meta: NodeMeta) extends BuilderFailWithMeta
  final case class ParentCannotBeGeneric(tpe: IzTypeId, problematic: IzTypeReference, meta: NodeMeta) extends BuilderFailWithMeta
  final case class TopLevelScalarOrBuiltinGenericExpected(reference: IzTypeReference, result: IzTypeReference) extends BuilderFail
  final case class TemplateArgumentClash(tpe: IzTypeId, clashed: Set[IzTypeArgName]) extends BuilderFail
  final case class TemplateExpected(reference: IzTypeReference, got: IzType) extends BuilderFail
  final case class TemplateArgumentsCountMismatch(tpe: IzTypeId, expected: Int, got: Int) extends BuilderFail
  final case class GenericExpected(reference: IzTypeReference.Generic, got: IzType) extends BuilderFail

  final case class CannotApplyTypeModifiers(tpe: IzTypeId, problematic: IzTypeId, meta: NodeMeta) extends BuilderFailWithMeta

  final case class EnumExpected(tpe: IzTypeId, problematic: IzTypeId, meta: NodeMeta) extends BuilderFailWithMeta
  final case class EnumExpectedButGotGeneric(tpe: IzTypeId, problematic: IzTypeReference, meta: NodeMeta) extends BuilderFailWithMeta

  final case class UnexpectedArguments(context: IzTypeId, problems: Seq[InterpContext], meta: NodeMeta) extends BuilderFailWithMeta
  final case class UnexpectedAdtCloneModifiers(context: IzTypeId, meta: NodeMeta) extends BuilderFailWithMeta
  final case class UnexpectedStructureCloneModifiers(context: IzTypeId, meta: NodeMeta) extends BuilderFailWithMeta
  final case class FeatureUnsupported(context: IzTypeId, explanation: String, meta: NodeMeta) extends BuilderFailWithMeta
  final case class GenericAdtBranchMustBeNamed(context: IzTypeId, problematic: RawRef, meta: NodeMeta) extends BuilderFailWithMeta
  final case class CannotApplyAdtBranchContract(context: IzTypeId, branch: RawDeclaredTypeName, meta: NodeMeta) extends BuilderFailWithMeta
  final case class InterfaceTypeCannotBeUsedInAdt(context: IzTypeId, problematic: RawDeclaredTypeName, meta: NodeMeta) extends BuilderFailWithMeta

  sealed trait ConstFail extends T2Fail
  final case class InferenceFailed(name: String, values: Set[IzTypeReference], meta: RawConstMeta) extends ConstFail

  sealed trait VerificationFail extends BuilderFail

  final case class ContradictiveFieldDefinition(tpe: IzTypeId, field: FullField, conflicts: Seq[FieldConflict], meta: NodeMeta) extends VerificationFail
  final case class MissingTypespaceMembers(missingRefs: Map[IzTypeId, Set[IzTypeId]]) extends VerificationFail
  final case class DuplicatedTypespaceMembers(missingRefs: Set[IzTypeId]) extends VerificationFail
  final case class UnresolvedGenericsInstancesLeft(badRefs: Map[IzTypeId, Set[RefToTLTLink]]) extends VerificationFail
  final case class ContradictiveEnumMembers(id: IzTypeId,  badMembers: Map[String, Seq[EnumMember]]) extends VerificationFail
  final case class ContradictiveAdtMembers(id: IzTypeId,  badMembers: Map[String, Seq[AdtMember]]) extends VerificationFail
  final case class UndefinedForeignTypeParameters(id: IzTypeId, undefinedParameters: Set[IzTypeArgName], meta: NodeMeta) extends VerificationFail
  final case class MissingTypespaceMember(id: IzTypeId, context: IzTypeId, meta: NodeMeta) extends VerificationFail
  final case class NonUniqueMethodName(id: IzTypeId, issues: Map[String, List[IzMethod]], meta: NodeMeta) extends VerificationFail
}




