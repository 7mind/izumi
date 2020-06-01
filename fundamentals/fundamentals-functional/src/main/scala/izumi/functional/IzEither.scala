package izumi.functional

import scala.collection.compat._

trait IzEither {

  implicit final class EitherBiFoldLeft[L, R, Col[x] <: Iterable[x]](s: Col[R]) {
    def biFoldLeft[A](z: A)(op: (A, R) => Either[List[L], A]): Either[List[L], A] = {
      val i = s.iterator
      var acc: Either[List[L], A] = Right(z)

      while (i.hasNext && acc.isRight) {
        val nxt = i.next()
        (acc, nxt) match {
          case (Right(a), n) =>
            acc = op(a, n)
          case (o, _) =>
            o
        }
      }

      acc
    }
  }


  implicit final class EitherBiAggregate[L, R, Col[x] <: Iterable[x]](result: Col[Either[List[L], R]]) {
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

  implicit final class EitherBiFlatAggregate[L, R, Col[x] <: Iterable[x], Col2[x] <: Iterable[x]](result: Col[Either[List[L], Col2[R]]]) {
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

  implicit final class EitherBiSplit[L, R, Col[x] <: Iterable[x]](e: Col[Either[L, R]]) {
    def lrPartition(implicit bl: Factory[L, Col[L]], br: Factory[R, Col[R]]): (Col[L], Col[R]) = {
      val left = e.collect {
        case Left(l) => l
      }
      val right = e.collect {
        case Right(r) => r
      }
      (bl.fromSpecific(left), br.fromSpecific(right))
    }
  }

  implicit final class EitherBiFind[Col[x] <: Iterable[x], T](s: Col[T]) {
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
