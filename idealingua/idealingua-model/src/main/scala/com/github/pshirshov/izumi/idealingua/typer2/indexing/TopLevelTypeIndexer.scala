package com.github.pshirshov.izumi.idealingua.typer2.indexing

import com.github.pshirshov.izumi.idealingua.typer2.interpreter.Resolvers
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.IzStructure
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.{AdtMemberNested, AdtMemberRef}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.RefToTLTLink
import com.github.pshirshov.izumi.idealingua.typer2.model._

class TopLevelTypeIndexer(resolvers: Resolvers) {
  import TopLevelTypeIndexer._
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
          case IzTypeReference.Generic(id: IzTypeId.BuiltinTypeId, _, _) =>
            id
        }
    }

    val allBadTopLevelIds = allTopLevelRefs.mapValues {
      s =>
        s.collect {
          case g@IzTypeReference.Generic(id: IzTypeId.UserTypeId, _, _) =>
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
        refs(structure)
      case a: IzType.Adt =>
        refs(a.contract) ++ a.members
          .map {
            case ref: AdtMemberRef =>
              a.id -> ref.ref
            case n: AdtMemberNested =>
              a.id -> n.ref
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
      case s: IzType.TargetInterface =>
        s.methods.flatMap(refs(s)).toSet
    }
  }

  private def refs(context: IzType)(structure: IzMethod): Set[(IzTypeId, IzTypeReference)] = {
    (structure.input match {
      case IzInput.Singular(typeref) =>
        Set(context.id -> typeref)
    }) ++ (structure.output match {
      case basic: IzOutput.Basic =>
        refs(context, basic)
      case IzOutput.Alternative(success, failure) =>
        refs(context, success) ++ refs(context, failure)
    })
  }

  private def refs(context: IzType, basic: IzOutput.Basic): Set[(IzTypeId, IzTypeReference)] = {
    basic match {
      case IzOutput.Singular(typeref) =>
        Set(context.id -> typeref)
    }
  }

  private def refs(structure: IzStructure): Set[(IzTypeId, IzTypeReference)] = {
    structure.parents.map(p => structure.id -> IzTypeReference.Scalar(p)).toSet ++
      structure.fields.map(f => structure.id -> f.tpe).toSet
  }
}

object TopLevelTypeIndexer {
  case class TopLevelIdIndex(
                              allIds: Set[IzTypeId],
                              present: Map[IzTypeId, Set[IzTypeId]],
                              missingGenerics: Map[IzTypeId, Set[RefToTLTLink]],
                            )

}
