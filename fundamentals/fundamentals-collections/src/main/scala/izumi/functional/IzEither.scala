package izumi.functional

import izumi.fundamentals.collections.nonempty.NEList

import scala.collection.compat.*
import scala.language.implicitConversions

trait IzEither {
  import izumi.functional.IzEither.*

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

  @inline implicit final def EitherTo[L, R, ColR[x] <: IterableOnce[x]](col: Either[L, ColR[R]]): EitherTo[ColR, L, R] = new EitherTo(col)

  @inline protected implicit final def EitherAccumulate[A, ColR[x] <: IterableOnce[x]](col: ColR[A]): EitherAccumulate[A, ColR] = new EitherAccumulate(col)
}

object IzEither extends IzEither {

  final class EitherExt(private val e: Either.type) extends AnyVal {
    def ifThenFail[A](cond: Boolean)(fun: => A): Either[A, Unit] = {
      if (cond) {
        Left(fun)
      } else {
        Right(())
      }
    }

    def ifThenElse[L, R](cond: Boolean)(ok: => R)(fail: => L): Either[L, R] = {
      if (cond) {
        Right(ok)
      } else {
        Left(fail)
      }
    }
  }

  protected final class EitherAccumulate[A, ColR[x] <: IterableOnce[x]](private val col: ColR[A]) extends AnyVal {

    @inline def accumulateErrors[ColL[_], L, R, L1, R1](
      map: A => Either[L, R],
      onLeft: L => IterableOnce[L1],
      onRight: R => Unit,
      end: () => R1,
    )(implicit buildL: Factory[L1, ColL[L1]]
    ): Either[ColL[L1], R1] = {
      val bad = buildL.newBuilder

      val iterator = col.iterator
      var allGood = true
      while (iterator.hasNext) {
        map(iterator.next()) match {
          case Left(e) =>
            allGood = false
            bad ++= onLeft(e)
          case Right(v) =>
            onRight(v)
        }
      }

      if (allGood) {
        Right(end())
      } else {
        Left(bad.result())
      }
    }
  }

  final class EitherBiAggregate[L, R, ColL[_], ColR[x] <: IterableOnce[x]](private val col: ColR[Either[ColL[L], R]]) extends AnyVal {
    /** `sequence` with error accumulation */
    @deprecated("use .biSequence")
    def biAggregate(implicit iterL: ColL[L] => IterableOnce[L], buildR: Factory[R, ColR[R]], buildL: Factory[L, ColL[L]]): Either[ColL[L], ColR[R]] = { biSequence }

    def biSequence(implicit iterL: ColL[L] => IterableOnce[L], buildR: Factory[R, ColR[R]], buildL: Factory[L, ColL[L]]): Either[ColL[L], ColR[R]] = {
      val good = buildR.newBuilder
      col.accumulateErrors(identity, (l: ColL[L]) => iterL(l), (v: R) => good += v, () => good.result())
    }

    @deprecated("use .biSequence_")
    def biAggregateVoid(implicit iterL: ColL[L] => IterableOnce[L], buildL: Factory[L, ColL[L]]): Either[ColL[L], Unit] = {
      biSequence_
    }

    /** `sequence_` with error accumulation */
    def biSequence_(implicit iterL: ColL[L] => IterableOnce[L], buildL: Factory[L, ColL[L]]): Either[ColL[L], Unit] = {
      col.accumulateErrors(identity, (l: ColL[L]) => iterL(l), (_: R) => (), () => ())
    }
  }

  /** `sequence` with error accumulation */
  final class EitherScalarOps[L, R, ColR[x] <: IterableOnce[x]](private val col: ColR[Either[L, R]]) extends AnyVal {
    @deprecated("use .biSequenceScalar")
    def biAggregateScalar(implicit buildR: Factory[R, ColR[R]]): Either[NEList[L], ColR[R]] = {
      biSequenceScalar
    }

    def biSequenceScalar(implicit buildR: Factory[R, ColR[R]]): Either[NEList[L], ColR[R]] = {
      val good = buildR.newBuilder
      col.accumulateErrors(identity, (l: L) => Seq(l), (v: R) => good ++= Seq(v), () => good.result())
    }
  }

  final class EitherTo[ColR[x] <: IterableOnce[x], L, R](private val col: Either[L, ColR[R]]) extends AnyVal {
    def to[CC](buildR: Factory[R, CC]): Either[L, CC] = {
      // just col.map(_.to(buildR)) doesn't work on 2.12
      col.map {
        r =>
          val b = buildR.newBuilder
          b ++= r.iterator
          b.result()
      }
    }
  }

  final class EitherBiMapAggregate[ColR[x] <: IterableOnce[x], T](private val col: ColR[T]) extends AnyVal {
    @deprecated("use .biTraverse")
    def biMapAggregate[ColL[_], L, A](
      f: T => Either[ColL[L], A]
    )(implicit buildR: Factory[A, ColR[A]],
      buildL: Factory[L, ColL[L]],
      iterL: ColL[L] => IterableOnce[L],
    ): Either[ColL[L], ColR[A]] = {
      biTraverse(f)
    }

    /** `traverse` with error accumulation */
    def biTraverse[ColL[_], L, A](
      f: T => Either[ColL[L], A]
    )(implicit buildR: Factory[A, ColR[A]],
      buildL: Factory[L, ColL[L]],
      iterL: ColL[L] => IterableOnce[L],
    ): Either[ColL[L], ColR[A]] = {
      val good = buildR.newBuilder
      col.accumulateErrors(f, (l: ColL[L]) => iterL(l), (v: A) => good += v, () => good.result())
    }

    @deprecated("use .biTraverse(f).to(...)")
    def biMapAggregateTo[ColL[_], L, A, CC](
      f: T => Either[ColL[L], A]
    )(buildRR: Factory[A, CC]
    )(implicit iterL: ColL[L] => IterableOnce[L],
      buildL: Factory[L, ColL[L]],
      buildR: Factory[A, ColR[A]],
    ): Either[ColL[L], CC] = {
      biTraverse(f).to(buildRR)
    }

    @deprecated("use .biTraverse_")
    def biMapAggregateVoid[ColL[_], L](f: T => Either[ColL[L], Unit])(implicit buildL: Factory[L, ColL[L]], iterL: ColL[L] => IterableOnce[L]): Either[ColL[L], Unit] = {
      biTraverse_(f)
    }

    /** `traverse_` with error accumulation */
    def biTraverse_[ColL[_], L](f: T => Either[ColL[L], Unit])(implicit buildL: Factory[L, ColL[L]], iterL: ColL[L] => IterableOnce[L]): Either[ColL[L], Unit] = {
      col.accumulateErrors(f, (l: ColL[L]) => iterL(l), (_: Unit) => (), () => ())
    }
  }

  final class EitherBiFlatMapAggregate[ColR[x] <: IterableOnce[x], T](private val col: ColR[T]) extends AnyVal {
    /** `flatTraverse` with error accumulation */

    @deprecated("use .biFlatTraverse")
    def biFlatMapAggregate[ColL[_], L, A](
      f: T => Either[ColL[L], IterableOnce[A]]
    )(implicit buildR: Factory[A, ColR[A]],
      buildL: Factory[L, ColL[L]],
      iterL: ColL[L] => IterableOnce[L],
    ): Either[ColL[L], ColR[A]] = {
      biFlatTraverse(f)
    }

    def biFlatTraverse[ColL[_], L, A](
      f: T => Either[ColL[L], IterableOnce[A]]
    )(implicit buildR: Factory[A, ColR[A]],
      buildL: Factory[L, ColL[L]],
      iterL: ColL[L] => IterableOnce[L],
    ): Either[ColL[L], ColR[A]] = {
      val good = buildR.newBuilder
      col.accumulateErrors(f, (l: ColL[L]) => iterL(l), (v: IterableOnce[A]) => good ++= v, () => good.result())
    }

    @deprecated("use .biFlatTraverse(f).to(...)")
    def biFlatMapAggregateTo[ColL[_], L, A, CC](
      f: T => Either[ColL[L], IterableOnce[A]]
    )(buildRR: Factory[A, CC]
    )(implicit iterL: ColL[L] => IterableOnce[L],
      buildL: Factory[L, ColL[L]],
      buildR: Factory[A, ColR[A]],
    ): Either[ColL[L], CC] = {
      biFlatTraverse(f).to(buildRR)
    }
  }

  final class EitherBiFlatAggregate[L, R, ColR[x] <: IterableOnce[x], ColIn[x] <: IterableOnce[x], ColL[_]](private val col: ColR[Either[ColL[L], ColIn[R]]])
    extends AnyVal {
    /** `flatSequence` with error accumulation */
    @deprecated("use .biFlatten")
    def biFlatAggregate(implicit buildR: Factory[R, ColR[R]], buildL: Factory[L, ColL[L]], iterL: ColL[L] => IterableOnce[L]): Either[ColL[L], ColR[R]] =
      biFlatten

    def biFlatten(implicit buildR: Factory[R, ColR[R]], buildL: Factory[L, ColL[L]], iterL: ColL[L] => IterableOnce[L]): Either[ColL[L], ColR[R]] = {
      col.biFlatTraverse(identity)
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
    // this doesn't play well with intellisense
    // def partition()(implicit bl: Factory[L, Col[L]], br: Factory[R, Col[R]]): (Col[L], Col[R]) = biPartition

    @deprecated("use .biPartition instead")
    def lrPartition(implicit bl: Factory[L, Col[L]], br: Factory[R, Col[R]]): (Col[L], Col[R]) = biPartition

    def biPartition(implicit bl: Factory[L, Col[L]], br: Factory[R, Col[R]]): (Col[L], Col[R]) = {
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
