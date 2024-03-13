package izumi.functional.bio

import izumi.fundamentals.collections.nonempty.NEList

import scala.collection.compat.*
import scala.collection.compat.immutable.LazyList
import scala.collection.compat.immutable.LazyList.#::
import scala.collection.immutable.Queue

trait ErrorAccumulatingOps2[F[+_, +_]] { this: Error2[F] =>

  /** `traverse` with error accumulation */
  def traverseAccumErrors[ColR[x] <: IterableOnce[x], ColL[_], E, A, B](
    col: ColR[A]
  )(f: A => F[ColL[E], B]
  )(implicit
    buildR: Factory[B, ColR[B]],
    buildL: Factory[E, ColL[E]],
    iterL: ColL[E] => IterableOnce[E],
  ): F[ColL[E], ColR[B]] = {
    accumulateErrorsImpl(col)(
      effect = f,
      onLeft = (l: ColL[E]) => iterL(l),
      init = Queue.empty[B],
      onRight = (acc: Queue[B], v: B) => acc :+ v,
      end = (acc: Queue[B]) => acc.to(buildR),
    )
  }

  /** `traverse_` with error accumulation */
  def traverseAccumErrors_[ColR[x] <: IterableOnce[x], ColL[_], E, A](
    col: ColR[A]
  )(f: A => F[ColL[E], Unit]
  )(implicit
    buildL: Factory[E, ColL[E]],
    iterL: ColL[E] => IterableOnce[E],
  ): F[ColL[E], Unit] = {
    accumulateErrorsImpl(col)(
      effect = f,
      onLeft = (l: ColL[E]) => iterL(l),
      init = (),
      onRight = (acc: Unit, _: Unit) => acc,
      end = (acc: Unit) => acc,
    )
  }

  /** `sequence` with error accumulation */
  def sequenceAccumErrors[ColR[x] <: IterableOnce[x], ColL[_], E, A](
    col: ColR[F[ColL[E], A]]
  )(implicit
    buildR: Factory[A, ColR[A]],
    buildL: Factory[E, ColL[E]],
    iterL: ColL[E] => IterableOnce[E],
  ): F[ColL[E], ColR[A]] = {
    traverseAccumErrors(col)(identity)
  }

  /** `sequence_` with error accumulation */
  def sequenceAccumErrors_[ColR[x] <: IterableOnce[x], ColL[_], E, A](
    col: ColR[F[ColL[E], A]]
  )(implicit
    buildL: Factory[E, ColL[E]],
    iterL: ColL[E] => IterableOnce[E],
  ): F[ColL[E], Unit] = {
    traverseAccumErrors_(col)(void(_))
  }

  /** `sequence` with error accumulation */
  def sequenceAccumErrorsNEList[ColR[x] <: IterableOnce[x], E, A](
    col: ColR[F[E, A]]
  )(implicit buildR: Factory[A, ColR[A]]
  ): F[NEList[E], ColR[A]] = {
    accumulateErrorsImpl(col)(
      effect = identity,
      onLeft = (e: E) => Seq(e),
      init = Queue.empty[A],
      onRight = (ac: Queue[A], a: A) => ac :+ a,
      end = (ac: Queue[A]) => ac.to(buildR),
    )
  }

  /** `flatTraverse` with error accumulation */
  def flatTraverseAccumErrors[ColR[x] <: IterableOnce[x], ColIn[x] <: IterableOnce[x], ColL[_], E, A, B](
    col: ColR[A]
  )(f: A => F[ColL[E], ColIn[B]]
  )(implicit
    buildR: Factory[B, ColR[B]],
    buildL: Factory[E, ColL[E]],
    iterL: ColL[E] => IterableOnce[E],
  ): F[ColL[E], ColR[B]] = {
    accumulateErrorsImpl(col)(
      effect = f,
      onLeft = (l: ColL[E]) => iterL(l),
      init = Queue.empty[B],
      onRight = (acc: Queue[B], v: IterableOnce[B]) => acc ++ v,
      end = (acc: Queue[B]) => acc.to(buildR),
    )
  }

  /** `flatSequence` with error accumulation */
  def flatSequenceAccumErrors[ColR[x] <: IterableOnce[x], ColIn[x] <: IterableOnce[x], ColL[_], E, A](
    col: ColR[F[ColL[E], ColIn[A]]]
  )(implicit
    buildR: Factory[A, ColR[A]],
    buildL: Factory[E, ColL[E]],
    iterL: ColL[E] => IterableOnce[E],
  ): F[ColL[E], ColR[A]] = {
    flatTraverseAccumErrors(col)(identity)
  }

  protected[this] def accumulateErrorsImpl[ColL[_], ColR[x] <: IterableOnce[x], E, E1, A, B, B1, AC](
    col: ColR[A]
  )(effect: A => F[E, B],
    onLeft: E => IterableOnce[E1],
    init: AC,
    onRight: (AC, B) => AC,
    end: AC => B1,
  )(implicit buildL: Factory[E1, ColL[E1]]
  ): F[ColL[E1], B1] = {
    def go(
      bad: Queue[E1],
      good: AC,
      lazyList: LazyList[A],
      allGood: Boolean,
    ): F[ColL[E1], B1] = {
      lazyList match {
        case h #:: tail =>
          redeem(effect(h))(
            e => go(bad ++ onLeft(e), good, tail, allGood = false),
            v => {
              val newGood = onRight(good, v)
              go(bad, newGood, tail, allGood)
            },
          )
        case _ =>
          if (allGood) {
            pure(end(good))
          } else {
            fail(bad.to(buildL))
          }
      }
    }

    go(Queue.empty[E1], init, col.iterator.to(LazyList), allGood = true)
  }

}
