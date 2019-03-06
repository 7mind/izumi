package com.github.pshirshov.izumi.idealingua.typer2.results

import com.github.pshirshov.izumi.idealingua.typer2.model.IzType
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail.BuilderFail

protected trait ResultBase {
  type Result[T] = Either[List[BuilderFail], T]
  type TList = Result[List[IzType]]
  type TSingle = Result[IzType]
  type TSingleT[T <: IzType] = Result[T]

  implicit class TSingleExt(ret: TSingle) {
    def asList: TList = ret.map(v => List(v))
  }

//    implicit class EitherFolderExt[L, R](result: Seq[Either[L, R]]) {
//      def aggregate: Either[List[L], List[R]] = {
//        val bad = result.collect({ case Left(e) => e })
//        if (bad.isEmpty) {
//          Right(result.collect({ case Right(r) => r }).toList)
//        } else {
//          Left(bad.toList)
//        }
//      }
//    }

  //  implicit class EitherFolderExt1[L, R](result: Seq[Either[L, Traversable[R]]]) {
  //    def flatAggregate: Either[List[L], List[R]] = {
  //      val bad = result.collect({ case Left(e) => e })
  //      if (bad.isEmpty) {
  //        Right(result.collect({ case Right(r) => r }).flatten.toList)
  //      } else {
  //        Left(bad.toList)
  //      }
  //    }
  //  }

  implicit class EitherFolderExt1[L, R](result: Seq[Either[List[L], R]]) {
    def biAggregate: Either[List[L], List[R]] = {
      val bad = result.collect({ case Left(e) => e })
      if (bad.isEmpty) {
        Right(result.collect({ case Right(r) => r }).toList)
      } else {
        Left(bad.flatten.toList)
      }
    }
  }

  implicit class EitherFolderExt2[L, R](result: Seq[Either[List[L], Traversable[R]]]) {
    def biFlatAggregate: Either[List[L], List[R]] = {
      val bad = result.collect({ case Left(e) => e })
      if (bad.isEmpty) {
        Right(result.collect({ case Right(r) => r }).flatten.toList)
      } else {
        Left(bad.flatten.toList)
      }
    }
  }

}
