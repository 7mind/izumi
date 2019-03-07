package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.idealingua.typer2.interpreter.Resolvers
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.IzStructure
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.{AdtMemberNested, AdtMemberRef}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.RefToTLTLink
import com.github.pshirshov.izumi.idealingua.typer2.model.{IzType, IzTypeId, IzTypeReference}

class TypespaceEvalutor(resolvers: Resolvers) {
  import TypespaceEvalutor._
  def topLevelIndex(allTypes: List[IzType]): TopLevelIdIndex = {
    val allTypeIds: Set[IzTypeId] = allTypes.map(_.id).toSet

    val allTopLevelRefs: Map[IzTypeId, Set[IzTypeReference]] = allTypes
      .flatMap(collectTopLevelReferences)
      .groupBy(_._1)
      .mapValues(_.map(pair => resolvers.refToTopId2(pair._2)).toSet)

    val allTopLevelIds = allTopLevelRefs.mapValues {
      s =>
        s.collect {
          case s: IzTypeReference.Scalar =>
            s.id
          case IzTypeReference.Generic(id: IzTypeId.BuiltinType, _, _) =>
            id
        }
    }

    val allBadTopLevelIds = allTopLevelRefs.mapValues {
      s =>
        s.collect {
          case g@IzTypeReference.Generic(id: IzTypeId.UserType, _, _) =>
            RefToTLTLink(g, id)
        }
    }.filter(_._2.nonEmpty)

    val topLevelIdIndex = TopLevelIdIndex(allTypeIds, allTopLevelIds, allBadTopLevelIds)
    topLevelIdIndex
  }

  private def collectTopLevelReferences(t: IzType): Set[(IzTypeId, IzTypeReference)] = {
    t match {
      case _: IzType.Generic =>
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
      case _: IzType.Foreign =>
        Set.empty
      case _: IzType.BuiltinType =>
        Set.empty
    }
  }

}

object TypespaceEvalutor {
  case class TopLevelIdIndex(
                              allIds: Set[IzTypeId],
                              present: Map[IzTypeId, Set[IzTypeId]],
                              missingGenerics: Map[IzTypeId, Set[RefToTLTLink]],
                            )

}
