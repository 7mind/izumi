package izumi.functional

import izumi.functional.IzEitherAggregations.{EitherScalarOps, EitherBiAggregate, EitherBiFlatAggregate, EitherBiFlatMapAggregate, EitherBiMapAggregate}
import izumi.fundamentals.collections.nonempty.NonEmptyList

import scala.collection.compat.*
import scala.language.implicitConversions

trait IzEitherAggregations extends IzEitherTraversals {
  @inline implicit final def EitherBiAggregate[L, R, Col[x] <: IterableOnce[x]](
    col: Col[Either[NonEmptyList[L], R]]
  ): EitherBiAggregate[L, R, Col] = new EitherBiAggregate(col)
  @inline implicit final def EitherBiFlatAggregate[L, R, Col[x] <: IterableOnce[x], Col2[x] <: IterableOnce[x]](
    col: Col[Either[NonEmptyList[L], Col2[R]]]
  ): EitherBiFlatAggregate[L, R, Col, Col2] = new EitherBiFlatAggregate(col)
  @inline implicit final def EitherBiMapAggregate[Col[x] <: IterableOnce[x], T](col: Col[T]): EitherBiMapAggregate[Col, T] = new EitherBiMapAggregate(col)
  @inline implicit final def EitherBiFlatMapAggregate[Col[x] <: IterableOnce[x], T](col: Col[T]): EitherBiFlatMapAggregate[Col, T] = new EitherBiFlatMapAggregate(col)
  @inline implicit final def EitherScalarOps[L, R, Col[x] <: IterableOnce[x]](col: Col[Either[L, R]]): EitherScalarOps[L, R, Col] = new EitherScalarOps(col)
}

object IzEitherAggregations extends IzEitherAggregations {

  final class EitherBiAggregate[L, R, Col[x] <: IterableOnce[x]](private val col: Col[Either[NonEmptyList[L], R]]) extends AnyVal {
    /** `sequence` with error accumulation */
    def biAggregate(implicit b: Factory[R, Col[R]]): Either[NonEmptyList[L], Col[R]] = {
      new IzEither.EitherBiAggregate(col)
        .biAggregate(b, _.toList)
        .left.map(NonEmptyList.unsafeFrom)
    }

    /** `sequence_` with error accumulation */
    def biAggregateVoid: Either[NonEmptyList[L], Unit] = {
      new IzEither.EitherBiAggregate(col)
        .biAggregateVoid(_.toList)
        .left.map(NonEmptyList.unsafeFrom)
    }
  }

  final class EitherBiFlatAggregate[L, R, Col[x] <: IterableOnce[x], Col2[x] <: IterableOnce[x]](private val result: Col[Either[NonEmptyList[L], Col2[R]]]) extends AnyVal {
    /** `flatSequence` with error accumulation */
    def biFlatAggregate(implicit b: Factory[R, Col[R]]): Either[NonEmptyList[L], Col[R]] = {
      new IzEither.EitherBiFlatAggregate(result)
        .biFlatAggregate(b, _.toList)
        .left.map(NonEmptyList.unsafeFrom)
    }
  }

  final class EitherBiMapAggregate[Col[x] <: IterableOnce[x], T](private val col: Col[T]) extends AnyVal {
    /** `traverse` with error accumulation */
    @inline def biMapAggregate[L, A](f: T => Either[NonEmptyList[L], A])(implicit b: Factory[A, Col[A]]): Either[NonEmptyList[L], Col[A]] = {
      biMapAggregateTo(f)(b)
    }

    /** `traverse` with error accumulation */
    def biMapAggregateTo[L, A, CC](f: T => Either[NonEmptyList[L], A])(b: Factory[A, CC]): Either[NonEmptyList[L], CC] = {
      IzEither.EitherBiMapAggregate(col)
        .biMapAggregateTo(f)(b)(_.toList)
        .left.map(NonEmptyList.unsafeFrom)
    }

    /** `traverse_` with error accumulation */
    def biMapAggregateVoid[L](f: T => Either[NonEmptyList[L], Unit]): Either[NonEmptyList[L], Unit] = {
      IzEither.EitherBiMapAggregate(col)
        .biMapAggregateVoid(f)(_.toList)
        .left.map(NonEmptyList.unsafeFrom)
    }
  }

  final class EitherBiFlatMapAggregate[Col[x] <: IterableOnce[x], T](private val col: Col[T]) extends AnyVal {
    /** `flatTraverse` with error accumulation */
    def biFlatMapAggregate[L, A](f: T => Either[NonEmptyList[L], IterableOnce[A]])(implicit b: Factory[A, Col[A]]): Either[NonEmptyList[L], Col[A]] = {
      biFlatMapAggregateTo(f)(b)
    }

    /** `flatTraverse` with error accumulation */
    def biFlatMapAggregateTo[L, A, CC](f: T => Either[NonEmptyList[L], IterableOnce[A]])(b: Factory[A, CC]): Either[NonEmptyList[L], CC] = {
      IzEither.EitherBiFlatMapAggregate(col)
        .biFlatMapAggregateTo(f)(b)(_.toList)
        .left.map(NonEmptyList.unsafeFrom)
    }
  }

  final class EitherScalarOps[L, R, Col[x] <: IterableOnce[x]](private val e: Col[Either[L, R]]) extends AnyVal {
    /** `sequence` with error accumulation */
    def biAggregateScalar(implicit b: Factory[R, Col[R]]): Either[NonEmptyList[L], Col[R]] = {
      IzEither.EitherScalarOps(e)
        .biAggregateScalar
        .left.map(NonEmptyList.unsafeFrom)
    }
  }
}
