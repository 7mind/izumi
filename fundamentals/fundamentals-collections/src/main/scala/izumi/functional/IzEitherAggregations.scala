package izumi.functional

import izumi.functional.IzEitherAggregations.*
import izumi.fundamentals.collections.nonempty.NonEmptyList

import scala.collection.compat.*
import scala.language.implicitConversions

trait IzEitherAggregations {
  @inline implicit final def EitherBiAggregate[L, R, Col[x] <: IterableOnce[x]](
    col: Col[Either[NonEmptyList[L], R]]
  ): EitherBiAggregate[L, R, Col] = new EitherBiAggregate(col)
  @inline implicit final def EitherBiFlatAggregate[L, R, Col[x] <: IterableOnce[x], Col2[x] <: IterableOnce[x]](
    col: Col[Either[NonEmptyList[L], Col2[R]]]
  ): EitherBiFlatAggregate[L, R, Col, Col2] = new EitherBiFlatAggregate(col)
  @inline implicit final def EitherBiFind[Col[x] <: IterableOnce[x], T](col: Col[T]): EitherBiFind[Col, T] = new EitherBiFind(col)
  @inline implicit final def EitherBiFoldLeft[Col[x] <: IterableOnce[x], T](col: Col[T]): EitherBiFoldLeft[Col, T] = new EitherBiFoldLeft(col)
  @inline implicit final def EitherBiMapAggregate[Col[x] <: IterableOnce[x], T](col: Col[T]): EitherBiMapAggregate[Col, T] = new EitherBiMapAggregate(col)
  @inline implicit final def EitherBiFlatMapAggregate[Col[x] <: IterableOnce[x], T](col: Col[T]): EitherBiFlatMapAggregate[Col, T] = new EitherBiFlatMapAggregate(col)
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

  final class EitherBiFind[Col[x] <: IterableOnce[x], T](private val s: Col[T]) extends AnyVal {
    def biFind[E](predicate: T => Either[NonEmptyList[E], Boolean]): Either[NonEmptyList[E], Option[T]] = {
      IzEither.EitherBiFind(s)
        .biFind(predicate(_).left.map(_.toList))
        .left.map(NonEmptyList.unsafeFrom)
    }
  }

  final class EitherBiFoldLeft[Col[x] <: IterableOnce[x], T](private val col: Col[T]) extends AnyVal {
    /** monadic `foldLeft` with error accumulation */
    def biFoldLeft[L, A](z: A)(op: (A, T) => Either[NonEmptyList[L], A]): Either[NonEmptyList[L], A] = {
      IzEither.EitherBiFoldLeft(col)
        .biFoldLeft(z)((a,t) => op(a,t).left.map(_.toList))
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
        .biMapAggregateTo(f(_).left.map(_.toList))(b)
        .left.map(NonEmptyList.unsafeFrom)
    }

    /** `traverse_` with error accumulation */
    def biMapAggregateVoid[L](f: T => Either[NonEmptyList[L], Unit]): Either[NonEmptyList[L], Unit] = {
      IzEither.EitherBiMapAggregate(col)
        .biMapAggregateVoid(f(_).left.map(_.toList))
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
        .biFlatMapAggregateTo(f(_).left.map(_.toList))(b)
        .left.map(NonEmptyList.unsafeFrom)
    }
  }

}
