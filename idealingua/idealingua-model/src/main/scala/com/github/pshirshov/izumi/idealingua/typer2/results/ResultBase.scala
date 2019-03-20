package com.github.pshirshov.izumi.idealingua.typer2.results

import com.github.pshirshov.izumi.functional.IzEither
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail.BuilderFail

protected trait ResultBase extends IzEither {
  type Result[+T] = Either[List[BuilderFail], T]
  type TList = Result[List[IzType]]
  type TSingle = Result[IzType]
  type TSingleT[+T <: IzType] = Result[T]

  implicit class TSingleExt(ret: TSingle) {
    def asList: TList = ret.map(v => List(v))
  }

}
