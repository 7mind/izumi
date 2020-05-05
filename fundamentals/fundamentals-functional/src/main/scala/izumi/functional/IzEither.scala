package izumi.functional

import scala.collection.compat._
import izumi.functional.IzEither._

import scala.language.implicitConversions

trait IzEither {
  @inline implicit final def EitherBiAggregate[L, R, Col[x] <: Iterable[x]](result: Col[Either[List[L], R]]): EitherBiAggregate[L, R, Col] = new EitherBiAggregate(result)
  @inline implicit final def EitherBiFlatAggregate[L, R, Col[x] <: Iterable[x], Col2[x] <: Iterable[x]](result: Col[Either[List[L], Col2[R]]]): EitherBiFlatAggregate[L, R, Col, Col2] = new EitherBiFlatAggregate(result)
  @inline implicit final def EitherBiSplit[L, R, Col[x] <: Iterable[x]](e: Col[Either[L, R]]): EitherBiSplit[L, R, Col] = new EitherBiSplit(e)
  @inline implicit final def EitherBiFind[Col[x] <: Iterable[x], T](s: Col[T]): EitherBiFind[Col, T] = new EitherBiFind(s)
}

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


object IzEither extends IzEither {

  final class EitherBiAggregate[L, R, Col[x] <: Iterable[x]](private val result: Col[Either[List[L], R]]) extends AnyVal {
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

  final class EitherBiFlatAggregate[L, R, Col[x] <: Iterable[x], Col2[x] <: Iterable[x]](private val result: Col[Either[List[L], Col2[R]]]) extends AnyVal {
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

  final class EitherBiSplit[L, R, Col[x] <: Iterable[x]](private val e: Col[Either[L, R]]) extends AnyVal {
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

  final class EitherBiFind[Col[x] <: Iterable[x], T](private val s: Col[T]) extends AnyVal {
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
