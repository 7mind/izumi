package izumi.functional

import izumi.functional.IzEither.{EitherBiAggregate, EitherBiFlatAggregate, EitherBiFlatMapAggregate, EitherBiMapAggregate, EitherBiTraversals, EitherExt, EitherLrPartitions, EitherScalarOps}
import izumi.fundamentals.collections.nonempty.NonEmptyList

import scala.collection.compat.*
import scala.language.implicitConversions

trait IzEither {
  @inline implicit final def EitherBiAggregate[L, R, Src[_], Col[x] <: IterableOnce[x]](
    col: Col[Either[Src[L], R]]
  ): EitherBiAggregate[L, R, Src, Col] = new EitherBiAggregate(col)

  @inline implicit final def EitherBiFlatAggregate[L, R, Col[x] <: IterableOnce[x], Col2[x] <: IterableOnce[x], Src[_]](
    col: Col[Either[Src[L], Col2[R]]]
  ): EitherBiFlatAggregate[L, R, Col, Col2, Src] = new EitherBiFlatAggregate(col)
  @inline implicit final def EitherScalarOps[L, R, Col[x] <: IterableOnce[x]](col: Col[Either[L, R]]): EitherScalarOps[L, R, Col] = new EitherScalarOps(col)

  @inline implicit final def EitherBiMapAggregate[Col[x] <: IterableOnce[x], T](col: Col[T]): EitherBiMapAggregate[Col, T] = new EitherBiMapAggregate(col)

  @inline implicit final def EitherBiFlatMapAggregate[Col[x] <: IterableOnce[x], T](col: Col[T]): EitherBiFlatMapAggregate[Col, T] = new EitherBiFlatMapAggregate(col)

  @inline implicit final def EitherObjectExt(e: Either.type): EitherExt = new EitherExt(e)

  @inline implicit final def EitherBiTraversals[Col[x] <: IterableOnce[x], T](col: Col[T]): EitherBiTraversals[Col, T] = new EitherBiTraversals(col)

  @inline implicit final def EitherLrPartitions[L, R, Col[x] <: IterableOnce[x]](col: Col[Either[L, R]]): EitherLrPartitions[L, R, Col] = new EitherLrPartitions(col)
}

object IzEither extends IzEither {

  final class EitherExt(private val e: Either.type) extends AnyVal {
    def failWhen[A](cond: Boolean)(fun: => A): Either[A, Unit] = {
      if (cond) {
        Left(fun)
      } else {
        Right(())
      }
    }
  }

  final class EitherBiAggregate[L, R, ColL[_], ColR[x] <: IterableOnce[x]](private val col: ColR[Either[ColL[L], R]]) extends AnyVal {
    /** `sequence` with error accumulation */
    def biAggregate(implicit iterL: ColL[L] => IterableOnce[L], buildR: Factory[R, ColR[R]], buildL: Factory[L, ColL[L]]): Either[ColL[L], ColR[R]] = {
      val bad = buildL.newBuilder
      val good = buildR.newBuilder

      val iterator = col.iterator
      while (iterator.hasNext) {
        iterator.next() match {
          case Left(e) => bad ++= iterL(e)
          case Right(v) => good += v
        }
      }

      val badList = bad.result()
      if (iterL(badList).iterator.isEmpty) {
        Right(good.result())
      } else {
        Left(badList)
      }
    }

    /** `sequence_` with error accumulation */
    def biAggregateVoid(implicit iterL: ColL[L] => IterableOnce[L], buildL: Factory[L, ColL[L]]): Either[ColL[L], Unit] = {
      val bad = buildL.newBuilder

      val iterator = col.iterator
      while (iterator.hasNext) {
        iterator.next() match {
          case Left(e) => bad ++= iterL(e)
          case _ =>
        }
      }

      val badList = bad.result()
      if (iterL(badList).iterator.isEmpty) {
        Right(())
      } else {
        Left(badList)
      }
    }
  }

  final class EitherBiMapAggregate[ColR[x] <: IterableOnce[x], T](private val col: ColR[T]) extends AnyVal {
    /** `traverse` with error accumulation */
    @inline def biMapAggregate[ColL[_], L, A](
      f: T => Either[ColL[L], A]
    )(implicit buildR: Factory[A, ColR[A]],
      buildL: Factory[L, ColL[L]],
      iterL: ColL[L] => IterableOnce[L],
    ): Either[ColL[L], ColR[A]] = {
      biMapAggregateTo(f)(buildR)
    }

    def biMapAggregateTo[ColL[_], L, A, CC](
      f: T => Either[ColL[L], A]
    )(buildR: Factory[A, CC]
    )(implicit iterL: ColL[L] => IterableOnce[L],
      buildL: Factory[L, ColL[L]],
    ): Either[ColL[L], CC] = {
      val bad = buildL.newBuilder
      val good = buildR.newBuilder

      val iterator = col.iterator
      while (iterator.hasNext) {
        f(iterator.next()) match {
          case Left(e) => bad ++= iterL(e)
          case Right(v) => good += v
        }
      }

      val badList = bad.result()
      if (iterL(badList).iterator.isEmpty) {
        Right(good.result())
      } else {
        Left(badList)
      }
    }

    /** `traverse_` with error accumulation */
    def biMapAggregateVoid[ColL[_], L](f: T => Either[ColL[L], Unit])(implicit buildL: Factory[L, ColL[L]], iterL: ColL[L] => IterableOnce[L]): Either[ColL[L], Unit] = {
      val bad = buildL.newBuilder

      val iterator = col.iterator
      while (iterator.hasNext) {
        f(iterator.next()) match {
          case Left(e) => bad ++= iterL(e)
          case _ =>
        }
      }

      val badList = bad.result()
      if (iterL(badList).iterator.isEmpty) {
        Right(())
      } else {
        Left(badList)
      }
    }
  }

  final class EitherBiFlatMapAggregate[ColR[x] <: IterableOnce[x], T](private val col: ColR[T]) extends AnyVal {
    /** `flatTraverse` with error accumulation */
    def biFlatMapAggregate[ColL[_], L, A](
      f: T => Either[ColL[L], IterableOnce[A]]
    )(implicit buildR: Factory[A, ColR[A]],
      buildL: Factory[L, ColL[L]],
      iterL: ColL[L] => IterableOnce[L],
    ): Either[ColL[L], ColR[A]] = {
      biFlatMapAggregateTo(f)(buildR)
    }

    /** `flatTraverse` with error accumulation */
    def biFlatMapAggregateTo[ColL[_], L, A, CC](
      f: T => Either[ColL[L], IterableOnce[A]]
    )(buildR: Factory[A, CC]
    )(implicit iterL: ColL[L] => IterableOnce[L],
      buildL: Factory[L, ColL[L]],
    ): Either[ColL[L], CC] = {
      val bad = buildL.newBuilder
      val good = buildR.newBuilder

      val iterator = col.iterator
      while (iterator.hasNext) {
        f(iterator.next()) match {
          case Left(e) => bad ++= iterL(e)
          case Right(v) => good ++= v
        }
      }

      val badList = bad.result()
      if (iterL(badList).iterator.isEmpty) {
        Right(good.result())
      } else {
        Left(badList)
      }
    }
  }

  final class EitherBiFlatAggregate[L, R, ColR[x] <: IterableOnce[x], ColIn[x] <: IterableOnce[x], ColL[_]](private val result: ColR[Either[ColL[L], ColIn[R]]])
    extends AnyVal {
    /** `flatSequence` with error accumulation */
    def biFlatAggregate(implicit buildR: Factory[R, ColR[R]], buildL: Factory[L, ColL[L]], iterL: ColL[L] => IterableOnce[L]): Either[ColL[L], ColR[R]] = {
      val bad = buildL.newBuilder
      val good = buildR.newBuilder

      val iterator = result.iterator
      while (iterator.hasNext) {
        iterator.next() match {
          case Left(e) => bad ++= iterL(e)
          case Right(v) => good ++= v
        }
      }

      val badList = bad.result()
      if (iterL(badList).iterator.isEmpty) {
        Right(good.result())
      } else {
        Left(badList)
      }
    }
  }

  /** `sequence` with error accumulation */
  final class EitherScalarOps[L, R, ColR[x] <: IterableOnce[x]](private val e: ColR[Either[L, R]]) extends AnyVal {

    def biAggregateScalarList(implicit buildR: Factory[R, ColR[R]]): Either[List[L], ColR[R]] = {
      biAggregateScalar.left.map(_.toList)
    }

    def biAggregateScalar(implicit buildR: Factory[R, ColR[R]]): Either[NonEmptyList[L], ColR[R]] = {
      val bad = List.newBuilder[L]
      val good = buildR.newBuilder

      e.iterator.foreach {
        case Left(e) => bad += e
        case Right(v) => good += v
      }

      val badList = bad.result()
      if (badList.isEmpty) {
        Right(good.result())
      } else {
        Left(NonEmptyList.unsafeFrom(badList))
      }
    }

  }

  final class EitherBiTraversals[Col[x] <: IterableOnce[x], T](private val col: Col[T]) extends AnyVal {
    def biFind[E](predicate: T => Either[E, Boolean]): Either[E, Option[T]] = {
      val i = col.iterator

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

    /** monadic `foldLeft` with error accumulation */
    def biFoldLeft[E, A](z: A)(op: (A, T) => Either[E, A]): Either[E, A] = {
      val i = col.iterator
      var acc: Either[E, A] = Right(z)

      while (i.hasNext && acc.isRight) {
        val nxt = i.next()
        (acc, nxt) match {
          case (Right(a), n) =>
            acc = op(a, n)
          case _ =>
        }
      }
      acc
    }

  }

  final class EitherLrPartitions[L, R, Col[x] <: IterableOnce[x]](col: Col[Either[L, R]]) {
    def lrPartition(implicit bl: Factory[L, Col[L]], br: Factory[R, Col[R]]): (Col[L], Col[R]) = {
      val bad = bl.newBuilder
      val good = br.newBuilder

      col.iterator.foreach {
        case Left(e) => bad += e
        case Right(v) => good += v
      }

      (bad.result(), good.result())
    }
  }
}
