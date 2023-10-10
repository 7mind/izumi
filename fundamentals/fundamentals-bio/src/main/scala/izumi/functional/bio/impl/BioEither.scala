package izumi.functional.bio.impl

import izumi.functional.bio.Error2

import scala.collection.compat.*
import scala.util.Try

object BioEither extends BioEither

open class BioEither extends Error2[Either] {

  @inline override final def pure[A](a: A): Either[Nothing, A] = Right(a)
  @inline override final def map[R, E, A, B](r: Either[E, A])(f: A => B): Either[E, B] = r.map(f)

  /** execute two operations in order, map their results */
  @inline override final def map2[R, E, A, B, C](firstOp: Either[E, A], secondOp: => Either[E, B])(f: (A, B) => C): Either[E, C] = {
    firstOp.flatMap(a => secondOp.map(b => f(a, b)))
  }
  @inline override final def flatMap[R, E, A, B](r: Either[E, A])(f: A => Either[E, B]): Either[E, B] = r.flatMap(f)

  @inline override final def catchAll[R, E, A, E2](r: Either[E, A])(f: E => Either[E2, A]): Either[E2, A] = r.left.flatMap(f)
  @inline override final def fail[E](v: => E): Either[E, Nothing] = Left(v)

  @inline override final def fromEither[E, V](effect: => Either[E, V]): Either[E, V] = effect
  @inline override final def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): Either[E, A] = effect match {
    case Some(value) => Right(value)
    case None => Left(errorOnNone)
  }
  @inline override final def fromTry[A](effect: => Try[A]): Either[Throwable, A] = effect.toEither

  @inline override final def guarantee[R, E, A](f: Either[E, A], cleanup: Either[Nothing, Unit]): Either[E, A] = f

  override def traverse[R, E, A, B](l: Iterable[A])(f: A => Either[E, B]): Either[E, List[B]] = {
    val b = List.newBuilder[B]
    val i = l.iterator

    while (i.hasNext) {
      f(i.next()) match {
        case Left(error) =>
          return Left(error)
        case Right(v) =>
          b += v
      }
    }
    Right(b.result())
  }

  override def foldLeft[R, E, A, AC](col: Iterable[A])(z: AC)(op: (AC, A) => Either[E, AC]): Either[E, AC] = {
    val i = col.iterator
    var acc: Either[E, AC] = Right(z)

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

  override def find[R, E, A](l: Iterable[A])(f: A => Either[E, Boolean]): Either[E, Option[A]] = {
    val i = l.iterator

    while (i.hasNext) {
      val a = i.next()
      f(a) match {
        case Left(value) =>
          return Left(value)
        case Right(true) =>
          return Right(Some(a))
        case Right(_) =>
      }
    }
    Right(None)
  }

  override def collectFirst[R, E, A, B](l: Iterable[A])(f: A => Either[E, Option[B]]): Either[E, Option[B]] = {
    val i = l.iterator

    while (i.hasNext) {
      val a = i.next()
      f(a) match {
        case Left(value) =>
          return Left(value)
        case Right(res @ Some(_)) =>
          return Right(res)
        case Right(_) =>
      }
    }
    Right(None)
  }

  override def partition[R, E, A](l: Iterable[Either[E, A]]): Right[Nothing, (List[E], List[A])] = {
    val bad = List.newBuilder[E]
    val good = List.newBuilder[A]

    l.iterator.foreach {
      case Left(e) => bad += e
      case Right(v) => good += v
    }

    Right((bad.result(), good.result()))
  }

  override protected[this] def accumulateErrorsImpl[ColL[_], ColR[x] <: IterableOnce[x], R, E, E1, A, B, B1, AC](
    col: ColR[A]
  )(effect: A => Either[E, B],
    onLeft: E => IterableOnce[E1],
    init: AC,
    onRight: (AC, B) => AC,
    end: AC => B1,
  )(implicit buildL: Factory[E1, ColL[E1]]
  ): Either[ColL[E1], B1] = {
    val bad = buildL.newBuilder

    val iterator = col.iterator
    var good = init
    var allGood = true
    while (iterator.hasNext) {
      effect(iterator.next()) match {
        case Left(e) =>
          allGood = false
          bad ++= onLeft(e)
        case Right(v) =>
          good = onRight(good, v)
      }
    }

    if (allGood) {
      Right(end(good))
    } else {
      Left(bad.result())
    }
  }

}
