package com.github.pshirshov.izumi.idealingua.typer2.indexing

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawConstMeta
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.IzTypeArgValue
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail.ConstMissingType
import com.github.pshirshov.izumi.idealingua.typer2.model.{IzType, IzTypeReference, T2Fail, Typespace2}

class InheritanceQueries(ts: Typespace2) {
  def isParent(name: String, meta: RawConstMeta)(parent: IzTypeReference, child: IzTypeReference): Either[List[T2Fail], Boolean] = {
    (parent, child) match {
      case (p: IzTypeReference.Scalar, c: IzTypeReference.Scalar) =>
        isParentScalar(name, meta)(p, c)
      case (
        IzTypeReference.Generic(idp, IzTypeArgValue(p: IzTypeReference.Scalar) :: Nil, _),
        IzTypeReference.Generic(idc, IzTypeArgValue(c: IzTypeReference.Scalar) :: Nil, _)
        ) if idc == idp && idc == IzType.BuiltinGeneric.TList.id =>
        isParentScalar(name, meta)(p, c)
      case (IzTypeReference.Generic(idp, IzTypeArgValue(kp: IzTypeReference.Scalar) :: IzTypeArgValue(p: IzTypeReference.Scalar) :: Nil, _), IzTypeReference.Generic(idc, IzTypeArgValue(kc: IzTypeReference.Scalar) :: IzTypeArgValue(c: IzTypeReference.Scalar) :: Nil, _)) if idc == idp && idc == IzType.BuiltinGeneric.TMap.id && kp == kc && kp.id == IzType.BuiltinScalar.TString.id =>
        isParentScalar(name, meta)(p, c)
      case (o1, o2) =>
        Right(o1 == o2)
    }
  }

  private def isParentScalar(name: String, meta: RawConstMeta)(parent: IzTypeReference.Scalar, child: IzTypeReference.Scalar): Either[List[T2Fail], Boolean] = {
    child match {
      case IzTypeReference.Scalar(id) if id == IzType.BuiltinScalar.TAny.id =>
        Right(true)
      case p: IzTypeReference.Scalar if p == parent =>
        Right(true)
      case IzTypeReference.Scalar(id) =>
        ts.index.get(id) match {
          case Some(value) =>
            value.member match {
              case structure: IzType.IzStructure =>
                Right(structure.allParents.contains(parent.id))
              case o =>
                Right(o.id == parent.id)
            }
          case None =>
            Left(List(ConstMissingType(name, child, meta)))
        }
    }
  }
}
