package izumi.functional

import scala.collection.compat._

trait IzEither {
  implicit final class EitherFolderExt1[L, R, Col[x] <: Iterable[x]](result: Col[Either[List[L], R]]) {
    def biAggregate(implicit b: Factory[R, Col[R]]): Either[List[L], Col[R]] = {
      val bad = result.collect {
        case Left(e) => e
      }
      if (bad.isEmpty) {
        Right(b.fromSpecific {
          result.collect {
            case Right(r) => r
          }
        })
      } else {
        Left(bad.flatten.toList)
      }
    }
  }

  implicit final class EitherFolderExt2[L, R, Col[x] <: Iterable[x]](result: Col[Either[List[L], Iterable[R]]]) {
    def biFlatAggregate(implicit b: Factory[R, Col[R]]): Either[List[L], Col[R]] = {
      val bad = result.collect {
        case Left(e) => e
      }
      if (bad.isEmpty) {
        Right(b.fromSpecific {
          result.collect {
            case Right(r) => r
          }.flatten
        })
      } else {
        Left(bad.flatten.toList)
      }
    }
  }

  implicit final class BiSplit[L, R, Col[x] <: Iterable[x]](e: Col[Either[L, R]]) {
    def lrPartition(implicit bl: Factory[L, Col[L]],  br: Factory[R, Col[R]]): (Col[L], Col[R]) = {
      val left = e.collect {
        case Left(l) => l
      }
      val right = e.collect {
        case Right(r) => r
      }
      (bl.fromSpecific(left), br.fromSpecific(right))
    }
  }

  implicit final class BiFind[T](s: Iterable[T]) {
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

object IzEither extends IzEither
