package izumi.functional

import izumi.functional.IzEither.{EitherBiAggregate, EitherBiFlatAggregate, EitherBiFlatMapAggregate, EitherBiMapAggregate, EitherScalarOps}

import scala.collection.compat.*
import scala.language.implicitConversions

trait IzEither extends IzEitherTraversals {
  @inline implicit final def EitherBiAggregate[L, R, Src[_], Col[x] <: IterableOnce[x]](
    col: Col[Either[Src[L], R]]
  ): EitherBiAggregate[L, R, Src, Col] = new EitherBiAggregate(col)
  @inline implicit final def EitherBiFlatAggregate[L, R, Col[x] <: IterableOnce[x], Col2[x] <: IterableOnce[x], Src[_]](
    col: Col[Either[Src[L], Col2[R]]]
  ): EitherBiFlatAggregate[L, R, Col, Col2, Src] = new EitherBiFlatAggregate(col)
  @inline implicit final def EitherScalarOps[L, R, Col[x] <: IterableOnce[x]](col: Col[Either[L, R]]): EitherScalarOps[L, R, Col] = new EitherScalarOps(col)
  @inline implicit final def EitherBiMapAggregate[Col[x] <: IterableOnce[x], T](col: Col[T]): EitherBiMapAggregate[Col, T] = new EitherBiMapAggregate(col)
  @inline implicit final def EitherBiFlatMapAggregate[Col[x] <: IterableOnce[x], T](col: Col[T]): EitherBiFlatMapAggregate[Col, T] = new EitherBiFlatMapAggregate(col)
}

object IzEither extends IzEither {

  final class EitherBiMapAggregate[Col[x] <: IterableOnce[x], T](private val col: Col[T]) extends AnyVal {
    /** `traverse` with error accumulation */
    @inline def biMapAggregate[Src[_], L, A](f: T => Either[Src[L], A])(implicit b: Factory[A, Col[A]], ev: Src[L] => IterableOnce[L]): Either[List[L], Col[A]] = {
      biMapAggregateTo(f)(b)
    }

    def biMapAggregateTo[Src[_], L, A, CC](f: T => Either[Src[L], A])(b: Factory[A, CC])(implicit ev: Src[L] => IterableOnce[L]): Either[List[L], CC] = {
      val bad = List.newBuilder[L]
      val good = b.newBuilder

      val iterator = col.iterator
      while (iterator.hasNext) {
        f(iterator.next()) match {
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
    def biMapAggregateVoid[Src[_], L](f: T => Either[Src[L], Unit])(implicit ev: Src[L] => IterableOnce[L]): Either[List[L], Unit] = {
      val bad = List.newBuilder[L]

      val iterator = col.iterator
      while (iterator.hasNext) {
        f(iterator.next()) match {
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

  final class EitherBiFlatMapAggregate[Col[x] <: IterableOnce[x], T](private val col: Col[T]) extends AnyVal {
    /** `flatTraverse` with error accumulation */
    def biFlatMapAggregate[Src[_], L, A](
      f: T => Either[Src[L], IterableOnce[A]]
    )(implicit b: Factory[A, Col[A]],
      ev: Src[L] => IterableOnce[L],
    ): Either[List[L], Col[A]] = {
      biFlatMapAggregateTo(f)(b)
    }

    /** `flatTraverse` with error accumulation */
    def biFlatMapAggregateTo[Src[_], L, A, CC](
      f: T => Either[Src[L], IterableOnce[A]]
    )(b: Factory[A, CC]
    )(implicit ev: Src[L] => IterableOnce[L]
    ): Either[List[L], CC] = {
      val bad = List.newBuilder[L]
      val good = b.newBuilder

      val iterator = col.iterator
      while (iterator.hasNext) {
        f(iterator.next()) match {
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

  final class EitherBiAggregate[L, R, Src[_], Col[x] <: IterableOnce[x]](private val col: Col[Either[Src[L], R]]) extends AnyVal {
    /** `sequence` with error accumulation */
    def biAggregate(implicit b: Factory[R, Col[R]], ev: Src[L] => IterableOnce[L]): Either[List[L], Col[R]] = {
      val bad = List.newBuilder[L]
      val good = b.newBuilder

      val iterator = col.iterator
      while (iterator.hasNext) {
        iterator.next() match {
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

    /** `sequence_` with error accumulation */
    def biAggregateVoid(implicit ev: Src[L] => IterableOnce[L]): Either[List[L], Unit] = {
      val bad = List.newBuilder[L]

      val iterator = col.iterator
      while (iterator.hasNext) {
        iterator.next() match {
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

  final class EitherBiFlatAggregate[L, R, Col[x] <: IterableOnce[x], Col2[x] <: IterableOnce[x], Src[_]](private val result: Col[Either[Src[L], Col2[R]]])
    extends AnyVal {
    /** `flatSequence` with error accumulation */
    def biFlatAggregate(implicit b: Factory[R, Col[R]], ev: Src[L] => IterableOnce[L]): Either[List[L], Col[R]] = {
      val bad = List.newBuilder[L]
      val good = b.newBuilder

      val iterator = result.iterator
      while (iterator.hasNext) {
        iterator.next() match {
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

  final class EitherScalarOps[L, R, Col[x] <: IterableOnce[x]](private val e: Col[Either[L, R]]) extends AnyVal {

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

}
