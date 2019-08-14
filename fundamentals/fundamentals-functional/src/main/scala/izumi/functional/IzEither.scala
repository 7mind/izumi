package izumi.functional

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

  implicit class EitherFolderExt2[L, R](result: Seq[Either[List[L], Iterable[R]]]) {
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

  implicit class BiFind[T](s: Seq[T]) {
    def biFind[E](predicate: T => Either[List[E], Boolean]): Either[List[E], Option[T]] = {
      val i = s.iterator
      while (i.hasNext) {
        val a = i.next()
        predicate(a) match {
          case Left(value) =>
            return Left(value)
          case Right(value) if value =>
            return Right(Some(a))

          case Right(_) =>
        }
      }
      Right(None)
    }
  }
}

object IzEither extends IzEither {

}
