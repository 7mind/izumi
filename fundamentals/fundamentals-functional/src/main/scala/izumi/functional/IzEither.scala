package izumi.functional

import izumi.functional.IzEither._

import scala.collection.compat._
import scala.language.implicitConversions

trait IzEither {
  @inline implicit final def EitherBiAggregate[L, R, Src[x] <: IterableOnce[x], Col[x] <: IterableOnce[x]](
    result: Col[Either[Src[L], R]]
  ): EitherBiAggregate[L, R, Src, Col] =
    new EitherBiAggregate(
      result
    )
  @inline implicit final def EitherBiFlatAggregate[L, R, Col[x] <: IterableOnce[x], Col2[x] <: IterableOnce[x]](
    result: Col[Either[List[L], Col2[R]]]
  ): EitherBiFlatAggregate[L, R, Col, Col2] = new EitherBiFlatAggregate(result)
  @inline implicit final def EitherScalarOps[L, R, Col[x] <: IterableOnce[x]](e: Col[Either[L, R]]): EitherScalarOps[L, R, Col] = new EitherScalarOps(e)
  @inline implicit final def EitherBiFind[Col[x] <: IterableOnce[x], T](s: Col[T]): EitherBiFind[Col, T] = new EitherBiFind(s)
  @inline implicit final def EitherBiFoldLeft[Col[x] <: IterableOnce[x], T](s: Col[T]): EitherBiFoldLeft[Col, T] = new EitherBiFoldLeft(s)
  @inline implicit final def EitherBiMapAggregate[Col[x] <: IterableOnce[x], T](s: Col[T]): EitherBiMapAggregate[Col, T] = new EitherBiMapAggregate(s)
  @inline implicit final def EitherBiFlatMapAggregate[Col[x] <: IterableOnce[x], T](result: Col[T]): EitherBiFlatMapAggregate[Col, T] = new EitherBiFlatMapAggregate(
    result
  )
}

object IzEither extends IzEither {

  final class EitherBiFoldLeft[Col[x] <: IterableOnce[x], T](private val result: Col[T]) extends AnyVal {
    /** monadic `foldLeft` with error accumulation */
    def biFoldLeft[L, A](z: A)(op: (A, T) => Either[List[L], A]): Either[List[L], A] = {
      val i = result.iterator
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

  final class EitherBiMapAggregate[Col[x] <: IterableOnce[x], T](private val result: Col[T]) extends AnyVal {
    /** `traverse` with error accumulation */
    @inline def biMapAggregate[L, A](f: T => Either[List[L], A])(implicit b: Factory[A, Col[A]]): Either[List[L], Col[A]] = {
      biMapAggregateTo(b)(f)
    }

    /** `traverse` with error accumulation */
    def biMapAggregateTo[L, A, CC](b: Factory[A, CC])(f: T => Either[List[L], A]): Either[List[L], CC] = {
      val bad = List.newBuilder[L]
      val good = b.newBuilder

      result.iterator.foreach {
        f(_) match {
          case Left(e) => bad ++= e
          case Right(v) => good += v
        }
      }

      val badList = bad.result()
      if (badList.isEmpty) {
        Right(good.result())
      } else {
        Left(badList)
      }
    }

    /** `traverse_` with error accumulation */
    def biMapAggregateVoid[L, A](f: T => Either[List[L], A]): Either[List[L], Unit] = {
      val bad = List.newBuilder[L]

      result.iterator.foreach {
        f(_) match {
          case Left(e) => bad ++= e
          case _ =>
        }
      }

      val badList = bad.result()
      if (badList.isEmpty) {
        Right(())
      } else {
        Left(badList)
      }
    }
  }

  final class EitherBiFlatMapAggregate[Col[x] <: IterableOnce[x], T](private val result: Col[T]) extends AnyVal {
    /** `flatTraverse` with error accumulation */
    def biFlatMapAggregate[L, A](f: T => Either[List[L], IterableOnce[A]])(implicit b: Factory[A, Col[A]]): Either[List[L], Col[A]] = {
      biFlatMapAggregateTo(f)(b)
    }

    /** `flatTraverse` with error accumulation */
    def biFlatMapAggregateTo[L, A, CC](f: T => Either[List[L], IterableOnce[A]])(b: Factory[A, CC]): Either[List[L], CC] = {
      val bad = List.newBuilder[L]
      val good = b.newBuilder

      result.iterator.foreach {
        f(_) match {
          case Left(e) => bad ++= e
          case Right(v) => good ++= v
        }
      }

      val badList = bad.result()
      if (badList.isEmpty) {
        Right(good.result())
      } else {
        Left(badList)
      }
    }
  }

  final class EitherBiAggregate[L, R, Src[x] <: IterableOnce[x], Col[x] <: IterableOnce[x]](private val result: Col[Either[Src[L], R]]) extends AnyVal {
    /** `sequence` with error accumulation */
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

    /** `sequence_` with error accumulation */
    def biAggregateVoid: Either[List[L], Unit] = {
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
    /** `flatSequence` with error accumulation */
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

    /** `sequence` with error accumulation */
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
          case Right(true) =>
            return Right(Some(a))
          case Right(_) =>
        }
      }
      Right(None)
    }
  }

}
