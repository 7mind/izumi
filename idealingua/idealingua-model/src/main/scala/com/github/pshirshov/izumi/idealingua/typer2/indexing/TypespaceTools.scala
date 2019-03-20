package com.github.pshirshov.izumi.idealingua.typer2.indexing

import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.IzStructure
import com.github.pshirshov.izumi.idealingua.typer2.model.{IzType, IzTypeId, Typespace2}

object TypespaceTools {
  implicit class Queries(ts2: Typespace2) {
    def asStructureUnsafe(member: IzTypeId): IzStructure = {
      ts2.index(member).member match {
        case structure: IzStructure =>
          structure
        case IzType.IzAlias(_, source, _) =>
          asStructureUnsafe(source.id)
        case o =>
          throw new IllegalStateException(s"$member is not a structure")
      }
    }
  }
}
