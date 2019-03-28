package com.github.pshirshov.izumi.idealingua.typer2.model

import com.github.pshirshov.izumi.idealingua.model.il.ast.InputPosition
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{InterpContext, RawConstMeta, RawVal}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.{RawDeclaredTypeName, RawRef}
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.{Operation, TypenameRef}
import com.github.pshirshov.izumi.idealingua.typer2.constants.ConstSupport.WIPConst
import com.github.pshirshov.izumi.idealingua.typer2.indexing.GoodImport
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model._
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.IzName
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.{IzTypeArgName, RefToTLTLink}
import com.github.pshirshov.izumi.idealingua.typer2.model.RestSpec.PathSegment.Parameter
import com.github.pshirshov.izumi.idealingua.typer2.model.RestSpec.QueryParameterName

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

  final case class StructureExpected(reference: IzTypeId, got: IzType) extends BuilderFail

  sealed trait ConstFail extends T2Fail
  final case class InferenceFailed(name: String, values: Set[IzTypeReference], meta: RawConstMeta) extends ConstFail
  final case class ConstantViolatesType(name: String, values: IzTypeReference, value: TypedVal, meta: RawConstMeta) extends ConstFail
  final case class RawConstantViolatesType(name: String, values: IzTypeReference, value: RawVal, meta: RawConstMeta) extends ConstFail
  final case class UndefinedField(name: String, context: IzTypeReference, field: FName, meta: RawConstMeta) extends ConstFail
  final case class ConstMissingType(name: String, problem: IzTypeReference, meta: RawConstMeta) extends ConstFail
  final case class TopLevelStructureExpected(name: String, problem: IzTypeReference, meta: RawConstMeta) extends ConstFail
  final case class ListExpectedToHaveOneTopLevelArg(problem: IzTypeReference) extends ConstFail
  final case class DuplicatedConstants(problems:  Map[TypedConstId, Seq[WIPConst]]) extends ConstFail
  final case class ConstCircularDependenciesDetected(loops: List[Set[TypedConstId]]) extends T2Fail
  final case class MissingConst(problem: TypedConstId, locations: Map[TypedConstId, Seq[InputPosition]]) extends T2Fail

  sealed trait VerificationFail extends BuilderFail

  final case class ContradictiveFieldDefinition(tpe: IzTypeId, field: FullField, conflicts: Seq[FieldConflict], meta: NodeMeta) extends VerificationFail
  final case class ProhibitedFieldType(tpe: IzTypeId, field: FullField, meta: NodeMeta) extends VerificationFail
  final case class MissingTypespaceMembers(missingRefs: Map[IzTypeId, Set[IzTypeId]]) extends VerificationFail
  final case class DuplicatedTypespaceMembers(missingRefs: Set[IzTypeId]) extends VerificationFail
  final case class UnresolvedGenericsInstancesLeft(badRefs: Map[IzTypeId, Set[RefToTLTLink]]) extends VerificationFail
  final case class ContradictiveEnumMembers(id: IzTypeId,  badMembers: Map[String, Seq[EnumMember]]) extends VerificationFail
  final case class ContradictiveAdtMembers(id: IzTypeId,  badMembers: Map[String, Seq[AdtMember]]) extends VerificationFail
  final case class UndefinedForeignTypeParameters(id: IzTypeId, undefinedParameters: Set[IzTypeArgName], meta: NodeMeta) extends VerificationFail
  final case class MissingTypespaceMember(id: IzTypeId, context: IzTypeId, meta: NodeMeta) extends VerificationFail
  final case class NonUniqueMethodName(id: IzTypeId, issues: Map[String, List[IzMethod]], meta: NodeMeta) extends VerificationFail

  sealed trait RestFail extends T2Fail

  final case class DuplicatedRestAnnos(id: IzTypeId, method: String) extends RestFail
  final case class UnexpectedAnnotationType(id: IzTypeId, method: String, badAnno: TypedConst) extends RestFail
  final case class UnexpectedValueType(id: IzTypeId, method: String, problem: TypedVal, name: String) extends RestFail
  final case class MissingValue(id: IzTypeId, method: String, name: String) extends RestFail
  final case class DuplicatedQueryParameters(service: IzTypeId, method: IzMethod, bad: Map[QueryParameterName, Set[String]]) extends RestFail
  final case class UnexpectedNonScalarMappingSegment(service: IzTypeId, method: IzMethod, basic: BasicField) extends RestFail
  final case class UnexpectedMappingTarget(service: IzTypeId, method: IzMethod, basic: BasicField) extends RestFail
  final case class UnexpectedGenericMappingArguments(service: IzTypeId, method: IzMethod, basic: BasicField) extends RestFail
  final case class MissingMappingField(service: IzTypeId, method: IzMethod, fieldName: FName) extends RestFail
  final case class UnexpectedNonScalarListElementMapping(service: IzTypeId, method: IzMethod, p: Parameter) extends RestFail
  final case class FailedToFindSignatureStructure(service: IzTypeId, method: IzMethod) extends RestFail


}




