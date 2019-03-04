package com.github.pshirshov.izumi.idealingua.typer2.model

import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2.ProcessedOp

case class Typespace2(

                       warnings: List[T2Warn],
                       imports: Set[IzTypeId],
                       types: List[ProcessedOp],
                     )

object Typespace2 {

  sealed trait ProcessedOp {
    def member: IzType
  }

  object ProcessedOp {

    final case class Exported(member: IzType) extends ProcessedOp

    final case class Imported(member: IzType) extends ProcessedOp

  }
}
