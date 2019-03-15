package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.idealingua.typer2.TypespaceEvalutor.TopLevelIdIndex
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.IzStructure
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.{FullField, NodeMeta}
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail._
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail.model.FieldConflict
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Warn.UnusedForeignTypeParameters
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2.ProcessedOp
import com.github.pshirshov.izumi.idealingua.typer2.model.{IzType, IzTypeId, IzTypeReference}


class TsVerifier(types: Map[IzTypeId, ProcessedOp], tsc: TypespaceEvalutor, logger: WarnLogger) {


  def validateTypespace(allTypes: List[IzType]): Either[List[VerificationFail], Unit] = {
    for {
      _ <- validateTsConsistency(allTypes)
      _ <- validateAll(allTypes, postValidate)
    } yield {

    }
  }

  private def validateTsConsistency(allTypes: List[IzType]): Either[List[VerificationFail], Unit] = {
    val topLevelIdIndex: TopLevelIdIndex = tsc.topLevelIndex(allTypes)

    val duplicatingDefs = allTypes.groupBy(_.id).filter(_._2.size > 1)


    val missingRefs = topLevelIdIndex.present.mapValues(_.diff(topLevelIdIndex.allIds)).filter(_._2.nonEmpty)
    for {
      _ <- if (duplicatingDefs.nonEmpty) {
        Left(List(DuplicatedTypespaceMembers(duplicatingDefs.keySet)))
      } else {
        Right(())
      }
      _ <- if (missingRefs.nonEmpty) {
        Left(List(MissingTypespaceMembers(missingRefs)))
      } else {
        Right(())
      }
      _ <- if (topLevelIdIndex.missingGenerics.nonEmpty) {
        Left(List(UnresolvedGenericsInstancesLeft(topLevelIdIndex.missingGenerics)))
      } else {
        Right(())
      }
    } yield {

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


  private def preValidate(tpe: IzType): Either[List[VerificationFail], Unit] = {
    // don't forget: we don't have ALL the definitions here yet
    tpe match {
      case interface: IzType.TargetInterface =>
        val issues = interface.methods.groupBy(_.name).filter(_._2.size > 1)
        if (issues.isEmpty) {
          Right(())
        } else {
          Left(List(NonUniqueMethodName(tpe.id, issues, interface.meta)))
        }
      case _ =>
        Right(())

    }
  }

  private def postValidate(tpe: IzType): Either[List[VerificationFail], Unit] = {
    tpe match {
      case structure: IzStructure =>
        merge(List(
          verifyFieldContradictions(structure.id, structure.meta, structure.fields),
        ))

      case i: IzType.Identifier =>
        merge(List(
          verifyFieldContradictions(i.id, i.meta, i.fields),
          verifyFieldTypes(i.id, i.meta, i.fields),
        ))

      case e: IzType.Enum =>
        merge(List(
          verifyEnumMemberContradictions(e),
        ))

      case a: IzType.Adt =>
        merge(List(
          verifyAdtBranchContradictions(a),
          verifyFieldContradictions(a.id, a.meta, a.contract.fields),
        ))

      case foreign: IzType.Foreign =>
        foreign match {
          case _: IzType.ForeignScalar =>
            Right(())
          case g: IzType.ForeignGeneric =>
            val usedParameters = g.mapping.flatMap(_._2.parameters).toSet
            val danglingParameters = g.args.toSet.diff(usedParameters)
            if (danglingParameters.nonEmpty) {
              logger.log(UnusedForeignTypeParameters(g.id, danglingParameters, g.meta))
            }
            val undefinedParameters = usedParameters.diff(g.args.toSet)

            if (undefinedParameters.isEmpty) {
              Right(())

            } else {
              Left(List(UndefinedForeignTypeParameters(g.id, undefinedParameters, g.meta)))
            }
        }


      case _ =>
        Right(())
    }
  }

  private def verifyAdtBranchContradictions(a: IzType.Adt): Either[List[VerificationFail], Unit] = {
    val badMembers = a.members.groupBy(m => m.name).filter(_._2.size > 1)
    if (badMembers.nonEmpty) {
      Left(List(ContradictiveAdtMembers(a.id, badMembers)))
    } else {
      Right(())
    }
  }

  private def verifyEnumMemberContradictions(e: IzType.Enum): Either[List[VerificationFail], Unit] = {
    val badMembers = e.members.groupBy(m => m.name).filter(_._2.size > 1)
    if (badMembers.nonEmpty) {
      Left(List(ContradictiveEnumMembers(e.id, badMembers)))
    } else {
      Right(())
    }
  }

  private def verifyFieldContradictions(id: IzTypeId, meta: NodeMeta, fields: Seq[FullField]): Either[List[VerificationFail], Unit] = {
    val badFields = fields.map {
      f =>
        f -> f.defined.map(_.as)
          .map(parent => FieldConflict(f.tpe, parent))
          .filterNot(c => isSubtype(c.tpe, c.expectedToBeParent))
    }
      .filterNot(_._2.isEmpty)
      .map {
        bad =>
          ContradictiveFieldDefinition(id, bad._1, bad._2, meta)
      }

    if (badFields.isEmpty) {
      Right(())
    } else {
      Left(badFields.toList)
    }
  }

  private def verifyFieldTypes(id: IzTypeId, meta: NodeMeta, fields: Seq[FullField]): Either[List[VerificationFail], Unit] = {
    val badFields = fields.filter {
      f =>
        Builtins.mappingSpecials.keySet.toSet[IzTypeId].contains(f.tpe.id)
    }.map {
      bad =>
        ProhibitedFieldType(id, bad, meta)
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
    import results._
    checks.biAggregate.map(_ => ())
  }

}
