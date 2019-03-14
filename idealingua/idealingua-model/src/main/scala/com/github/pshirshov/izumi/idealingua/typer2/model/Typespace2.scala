package com.github.pshirshov.izumi.idealingua.typer2.model

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains.Import
import com.github.pshirshov.izumi.idealingua.model.loader.FSPath
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2.ProcessedOp

case class Typespace2(
                       domainId: DomainId,
                       directImports: Seq[Import],
                       warnings: List[T2Warn],
                       imports: Set[IzTypeId],
                       types: List[ProcessedOp],
                       consts: List[TypedConst],
                       origin: FSPath,
                     ) {
  lazy val index: Map[IzTypeId, ProcessedOp] = types.map(t => t.member.id -> t).toMap
}

object Typespace2 {

  sealed trait ProcessedOp {
    def member: IzType
  }

  object ProcessedOp {

    final case class Exported(member: IzType) extends ProcessedOp

    final case class Imported(member: IzType) extends ProcessedOp

  }

}
