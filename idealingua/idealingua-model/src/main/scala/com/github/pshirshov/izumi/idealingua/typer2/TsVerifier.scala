package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.idealingua.typer2.interpreter.Resolvers
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.IzStructure
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.{AdtMemberNested, AdtMemberRef}
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail.model.FieldConflict
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail.{ContradictiveFieldDefinition, MissingTypespaceMembers, VerificationFail}
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2.ProcessedOp
import com.github.pshirshov.izumi.idealingua.typer2.model.{IzType, IzTypeId, IzTypeReference}

class TsVerifier(types: Map[IzTypeId, ProcessedOp], resolvers: Resolvers) {


  def validateTypespace(allTypes: List[IzType]): Either[List[VerificationFail], Unit] = {


    for {
      _ <- validateTsConsistency(allTypes)
      _ <- validateAll(allTypes, postValidate)
    } yield {

    }
  }

  private def validateTsConsistency(allTypes: List[IzType]): Either[List[VerificationFail], Unit] = {
    val allTypeIds = allTypes.map(_.id).toSet

    val allTopLevelRefs: Map[IzTypeId, Set[IzTypeReference]] = allTypes
      .flatMap(collectTopLevelReferences)
      .groupBy(_._1)
      .mapValues(_.map(pair => resolvers.refToTopId2(pair._2)).toSet)

    val allTopLevelIds = allTopLevelRefs.mapValues{
      s =>
        s.collect {
          case s: IzTypeReference.Scalar =>
            s.id
          case g@IzTypeReference.Generic(id: IzTypeId.BuiltinType, _, _) =>
            id

        }
    }

    val allBadTopLevelIds = allTopLevelRefs.mapValues{
      s =>
        s.collect {
          case g@IzTypeReference.Generic(id: IzTypeId.UserType, _, _) =>
            id
        }
    }.filter(_._2.nonEmpty)

    assert(allBadTopLevelIds.isEmpty)
    val missingRefs: Map[IzTypeId, Set[IzTypeId]] = allTopLevelIds.mapValues(_.diff(allTypeIds)).filter(_._2.nonEmpty)
    if (missingRefs.nonEmpty) {
      Left(List(MissingTypespaceMembers(missingRefs)))
    } else {
      Right(())
    }
  }

  def prevalidateTypes(allTypes: List[IzType]): Either[List[VerificationFail], Unit] = {
    validateAll(allTypes, preValidate)
  }

  private def validateAll(allTypes: List[IzType], validator: IzType => Either[List[VerificationFail], Unit]): Either[List[VerificationFail], Unit] = {
    val bad = allTypes
      .map(validator)
      .collect({ case Left(l) => l })
      .flatten

    for {
      _ <- if (bad.nonEmpty) {
        Left(bad)
      } else {
        Right(())
      }
    } yield {

    }
  }

  private def collectTopLevelReferences(t: IzType): Set[(IzTypeId, IzTypeReference)] = {
    t match {
      case generic: IzType.Generic =>
        Set.empty
      case structure: IzStructure =>
        structure.parents.map(p => structure.id -> IzTypeReference.Scalar(p)).toSet ++
          structure.fields.map(f => structure.id -> f.tpe).toSet
      case a: IzType.Adt =>
        a.members
          .map {
            case ref: AdtMemberRef =>
              a.id -> ref.ref
            case n: AdtMemberNested =>
              a.id -> n.tpe
          }
          .toSet
      case a: IzType.IzAlias =>
        Set(a.id -> a.source)
      case i: IzType.Identifier =>
        i.fields.map(f => i.id -> f.tpe).toSet
      case _: IzType.Enum =>
        Set.empty
      case foreign: IzType.Foreign =>
        Set.empty
      case builtinType: IzType.BuiltinType =>
        Set.empty
    }
  }

  private def preValidate(tpe: IzType): Either[List[VerificationFail], Unit] = {
    // TODO: verify
    // don't forget: we don't have ALL the definitions here yet
    Quirks.discard(tpe)
    Right(())
  }

  private def postValidate(tpe: IzType): Either[List[VerificationFail], Unit] = {
    tpe match {
      case structure: IzStructure =>
        merge(List(
          verifyFieldContradictions(structure),
        ))

      case o =>
        // TODO: member conflicts
        Right(())
      //      case generic: IzType.Generic =>
      //      case builtinType: IzType.BuiltinType =>
      //      case IzType.IzAlias(id, source, meta) =>
      //      case IzType.Identifier(id, fields, meta) =>
      //      case IzType.Enum(id, members, meta) =>
      //      case foreign: IzType.Foreign =>
      //      case IzType.Adt(id, members, meta) =>
    }
  }

  private def verifyFieldContradictions(structure: IzStructure): Either[List[VerificationFail], Unit] = {
    val badFields = structure.fields.map {
      f =>
        f -> f.defined.map(_.as)
          .map(parent => FieldConflict(f.tpe, parent))
          .filterNot(c => isSubtype(c.tpe, c.expectedToBeParent))
    }
      .filterNot(_._2.isEmpty)
      .map {
        bad =>
          ContradictiveFieldDefinition(structure.id, bad._1, bad._2, structure.meta)
      }

    if (badFields.isEmpty) {
      Right(())
    } else {
      Left(badFields.toList)
    }
  }


  private def isSubtype(child: IzTypeReference, parent: IzTypeReference): Boolean = {
    (child == parent) || {
      (child, parent) match {
        case (IzTypeReference.Scalar(childId), IzTypeReference.Scalar(parentId)) =>
          (types(childId).member, types(parentId).member) match {
            case (c: IzStructure, p: IzStructure) =>
              c.allParents.contains(p.id)
            case _ =>
              false
          }

        case _ =>
          false // all generics are non-covariant
      }
    }
  }

  private def merge(checks: List[Either[List[VerificationFail], Unit]]): Either[List[VerificationFail], Unit] = {
    val issues = checks
      .collect({ case Left(l) => l })
      .flatten
    if (issues.nonEmpty) {
      Left(issues)
    } else {
      Right(())
    }
  }

}
