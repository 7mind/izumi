package com.github.pshirshov.izumi.functional

// TODO: canbuildfrom/compat

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

  implicit class BiSplit[L, R](e: Seq[Either[L, R]]) {
    def lrPartition: (Seq[L], Seq[R]) = {
      val left = e.collect({ case Left(l) => l })
      val right = e.collect({ case Right(r) => r })
      (left, right)
    }
  }
}

object IzEither extends IzEither {

}
