package com.github.pshirshov.izumi.idealingua.typer2.indexing

import com.github.pshirshov.izumi.fundamentals.collections.ImmutableMultiMap
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.IzStructure
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.IzTypeArgValue
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2.ProcessedOp
import com.github.pshirshov.izumi.idealingua.typer2.model.{IzType, IzTypeId, IzTypeReference}

class InheritanceQueries(types: Map[IzTypeId, ProcessedOp]) {
  def isParent(parent: IzTypeReference, child: IzTypeReference): Either[Unit, Boolean] = {
    (parent, child) match {
      case (p: IzTypeReference.Scalar, c: IzTypeReference.Scalar) =>
        isParentScalar(p, c)
      case (
        IzTypeReference.Generic(idp, IzTypeArgValue(p: IzTypeReference.Scalar) :: Nil, _),
        IzTypeReference.Generic(idc, IzTypeArgValue(c: IzTypeReference.Scalar) :: Nil, _)
        ) if idc == idp && idc == IzType.BuiltinGeneric.TList.id =>
        isParentScalar(p, c)
      case (IzTypeReference.Generic(idp, IzTypeArgValue(kp: IzTypeReference.Scalar) :: IzTypeArgValue(p: IzTypeReference.Scalar) :: Nil, _), IzTypeReference.Generic(idc, IzTypeArgValue(kc: IzTypeReference.Scalar) :: IzTypeArgValue(c: IzTypeReference.Scalar) :: Nil, _)) if idc == idp && idc == IzType.BuiltinGeneric.TMap.id && kp == kc && kp.id == IzType.BuiltinScalar.TString.id =>
        isParentScalar(p, c)
      case (o1, o2) =>
        Right(o1 == o2)
    }
  }

  def allChildren(): ImmutableMultiMap[IzTypeId, IzTypeId] = {
    import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._
    types.map(_._2.member).collect({case s: IzStructure => s}).flatMap {
      s =>
        s.allParents.map {
          p =>
            p -> s.id
        }
    }.toMultimap
  }

  def allStructuralChildren(): ImmutableMultiMap[IzTypeId, IzTypeId] = {
    import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._
    types.map(_._2.member).collect({case s: IzStructure => s}).flatMap {
      s =>
        s.allStructuralParents.map {
          p =>
            p -> s.id
        }
    }.toMultimap
  }


  private def isParentScalar(parent: IzTypeReference.Scalar, child: IzTypeReference.Scalar): Either[Unit, Boolean] = {
    child match {
      case IzTypeReference.Scalar(id) if id == IzType.BuiltinScalar.TAny.id =>
        Right(true)
      case p: IzTypeReference.Scalar if p == parent =>
        Right(true)
      case IzTypeReference.Scalar(id) =>
        types.get(id) match {
          case Some(value) =>
            value.member match {
              case structure: IzType.IzStructure =>
                Right(structure.allParents.contains(parent.id))
              case o =>
                Right(o.id == parent.id)
            }
          case None =>
            Left(())
        }
    }
  }
}

