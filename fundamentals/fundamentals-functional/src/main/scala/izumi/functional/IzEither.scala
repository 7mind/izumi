package izumi.functional

import izumi.functional.IzEither._

import scala.collection.compat._
import scala.language.implicitConversions

trait IzEither {
  @inline implicit final def EitherBiAggregate[L, R, Col[x] <: IterableOnce[x]](result: Col[Either[List[L], R]]): EitherBiAggregate[L, R, Col] = new EitherBiAggregate(
    result
  )
  @inline implicit final def EitherBiFlatAggregate[L, R, Col[x] <: IterableOnce[x], Col2[x] <: IterableOnce[x]](
    result: Col[Either[List[L], Col2[R]]]
  ): EitherBiFlatAggregate[L, R, Col, Col2] = new EitherBiFlatAggregate(result)
  @inline implicit final def EitherScalarOps[L, R, Col[x] <: IterableOnce[x]](e: Col[Either[L, R]]): EitherScalarOps[L, R, Col] = new EitherScalarOps(e)
  @inline implicit final def EitherBiFind[Col[x] <: IterableOnce[x], T](s: Col[T]): EitherBiFind[Col, T] = new EitherBiFind(s)
  @inline implicit final def EitherBiFoldLeft[L, R, Col[x] <: IterableOnce[x]](s: Col[R]): EitherBiFoldLeft[L, R, Col] = new EitherBiFoldLeft(s)
}

object IzEither extends IzEither {

  final class EitherBiFoldLeft[L, R, Col[x] <: IterableOnce[x]](s: Col[R]) {
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

  final class EitherBiAggregate[L, R, Col[x] <: IterableOnce[x]](private val result: Col[Either[List[L], R]]) extends AnyVal {
    def biAggregate(implicit b: Factory[R, Col[R]]): Either[List[L], Col[R]] = {
      val bad = List.newBuilder[L]
      val good = b.newBuilder

      result.iterator.foreach {
        case Left(e) => bad ++= e
        case Right(v) => good += v
      }

      val badList = bad.result()
      if (badList.isEmpty) {
        Right(good.result())
      } else {
        Left(badList)
      }
    }

    def biAggregateSequence: Either[List[L], Unit] = {
      val bad = List.newBuilder[L]

      result.iterator.foreach {
        case Left(e) => bad ++= e
        case _ =>
      }

      val badList = bad.result()
      if (badList.isEmpty) {
        Right(())
      } else {
        Left(badList)
      }
    }
  }

  final class EitherBiFlatAggregate[L, R, Col[x] <: IterableOnce[x], Col2[x] <: IterableOnce[x]](private val result: Col[Either[List[L], Col2[R]]]) extends AnyVal {
    def biFlatAggregate(implicit b: Factory[R, Col[R]]): Either[List[L], Col[R]] = {
      val bad = List.newBuilder[L]
      val good = b.newBuilder

      result.iterator.foreach {
        case Left(e) => bad ++= e
        case Right(v) => good ++= v
      }

      val badList = bad.result()
      if (badList.isEmpty) {
        Right(good.result())
      } else {
        Left(badList)
      }
    }
  }

  final class EitherScalarOps[L, R, Col[x] <: IterableOnce[x]](private val e: Col[Either[L, R]]) extends AnyVal {
    def lrPartition(implicit bl: Factory[L, Col[L]], br: Factory[R, Col[R]]): (Col[L], Col[R]) = {
      val bad = bl.newBuilder
      val good = br.newBuilder

      e.iterator.foreach {
        case Left(e) => bad += e
        case Right(v) => good += v
      }

      (bad.result(), good.result())
    }

    def biAggregateScalar(implicit b: Factory[R, Col[R]]): Either[List[L], Col[R]] = {
      val bad = List.newBuilder[L]
      val good = b.newBuilder

      e.iterator.foreach {
        case Left(e) => bad += e
        case Right(v) => good += v
      }

      val badList = bad.result()
      if (badList.isEmpty) {
        Right(good.result())
      } else {
        Left(badList)
      }
    }
  }

  final class EitherBiFind[Col[x] <: IterableOnce[x], T](private val s: Col[T]) extends AnyVal {
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
