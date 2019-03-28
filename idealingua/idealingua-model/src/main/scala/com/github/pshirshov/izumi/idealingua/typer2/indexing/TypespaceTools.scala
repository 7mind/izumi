package com.github.pshirshov.izumi.idealingua.typer2.indexing

import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.IzStructure
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail.StructureExpected
import com.github.pshirshov.izumi.idealingua.typer2.model._

object TypespaceTools {
  implicit class Queries(ts2: Typespace2) {
    def inheritance: InheritanceQueries = new InheritanceQueries(ts2.index)

    def asStructure(member: IzTypeId): Either[List[T2Fail], IzStructure] = {
      ts2.index(member).member match {
        case structure: IzStructure =>
          Right(structure)
        case IzType.IzAlias(_, source, _) =>
          asStructure(source.id)
        case o =>
          Left(List(StructureExpected(member, o)))
      }
    }

    def resolveConst(id: TypedConstId): Either[List[T2Fail], TypedConst] = {
      val c = ts2.cindex(id).const
      c.value match {
        case TypedVal.TCRef(rid, _) =>
          resolveConst(rid)
        case _ => Right(c)
      }
    }
  }
}
