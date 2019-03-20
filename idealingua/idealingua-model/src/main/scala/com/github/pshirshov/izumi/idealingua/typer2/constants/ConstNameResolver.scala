package com.github.pshirshov.izumi.idealingua.typer2.constants

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawVal
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawRef
import com.github.pshirshov.izumi.idealingua.typer2.indexing.DomainIndex
import com.github.pshirshov.izumi.idealingua.typer2.interpreter.{Interpreter, ResolversImpl}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.BuiltinTypeId
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail.{ListExpectedToHaveOneTopLevelArg, TopLevelScalarOrBuiltinGenericExpected}
import com.github.pshirshov.izumi.idealingua.typer2.model.{IzType, IzTypeReference, T2Fail, TypedConstId}
import com.github.pshirshov.izumi.idealingua.typer2.results

class ConstNameResolver(index: DomainIndex) {
  import results._

  def toId(defScope: String, r: RawVal.CTypedRef): TypedConstId = {
    val did = r.domain.getOrElse(index.defn.id)
    val sid = r.scope.getOrElse(defScope)
    TypedConstId(did, sid, r.name)
  }

  def toId(defScope: String, r: RawVal.CRef): TypedConstId = {
    val did = r.domain.getOrElse(index.defn.id)
    val sid = r.scope.getOrElse(defScope)
    TypedConstId(did, sid, r.name)
  }

  def refToTopLevelRef1(ref: IzTypeReference): Either[List[T2Fail], IzTypeReference] = {
    ref match {
      case s: IzTypeReference.Scalar =>
        Right(s)

      case g@IzTypeReference.Generic(_: BuiltinTypeId, args, _) =>
        for {
          // to make sure all args are instantiated recursively
          _ <- args.map(a => refToTopLevelRef1(a.ref)).biAggregate
        } yield {
          g
        }

      case g: IzTypeReference.Generic =>
        Left(List(TopLevelScalarOrBuiltinGenericExpected(ref, g)))
    }
  }

  def refToTopLevelRef(ref: RawRef): Either[List[T2Fail], IzTypeReference] = {
    val r = new ResolversImpl(Interpreter.Args(Map.empty), index)
    val tref = r.refToTopId2(r.resolve(ref))
    refToTopLevelRef1(tref)
  }

  def listArgToTopLevelRef(ref: IzTypeReference): Either[List[T2Fail], IzTypeReference] = {
    ref match {
      case IzTypeReference.Generic(id, arg :: Nil, _) if id == IzType.BuiltinGeneric.TList.id =>
        refToTopLevelRef1(arg.ref)
      case o =>
        Left(List(ListExpectedToHaveOneTopLevelArg(o)))
    }

  }
}
