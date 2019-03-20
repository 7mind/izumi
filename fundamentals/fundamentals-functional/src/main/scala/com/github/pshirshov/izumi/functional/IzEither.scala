package com.github.pshirshov.izumi.functional

trait IzEither {
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

object IzEither extends IzEither {

}
