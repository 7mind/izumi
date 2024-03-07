package izumi.functional.bio

import scala.annotation.unused

trait Monad2[F[+_, +_]] extends Applicative2[F] {
  def flatMap[E, A, B](r: F[E, A])(f: A => F[E, B]): F[E, B]
  def flatten[E, A](r: F[E, F[E, A]]): F[E, A] = flatMap(r)(identity)

  def tailRecM[E, A, B](a: A)(f: A => F[E, Either[A, B]]): F[E, B] = {
    def loop(a: A): F[E, B] = flatMap(f(a)) {
      case Left(next) => loop(next)
      case Right(res) => pure(res)
    }

    flatMap[E, Unit, B](unit)(_ => loop(a)) // https://github.com/zio/interop-cats/pull/460
  }

  def tap[E, A](r: F[E, A], f: A => F[E, Unit]): F[E, A] = flatMap(r)(a => as(f(a))(a))

  @inline final def when[E, E1](cond: F[E, Boolean])(ifTrue: => F[E1, Unit])(implicit ev: E <:< E1): F[E1, Unit] = {
    ifThenElse(cond)(ifTrue, unit)
  }
  @inline final def unless[E, E1](cond: F[E, Boolean])(ifFalse: => F[E1, Unit])(implicit ev: E <:< E1): F[E1, Unit] = {
    ifThenElse(cond)(unit, ifFalse)
  }
  @inline final def ifThenElse[E, E1, A](
    cond: F[E, Boolean]
  )(ifTrue: => F[E1, A],
    ifFalse: => F[E1, A],
  )(implicit @unused ev: E <:< E1
  ): F[E1, A] = {
    flatMap(cond.asInstanceOf[F[E1, Boolean]])(if (_) ifTrue else ifFalse)
  }

  /** Extracts the optional value, or executes the `fallbackOnNone` effect */
  def fromOptionF[E, A](fallbackOnNone: => F[E, A], r: F[E, Option[A]]): F[E, A] = {
    flatMap(r) {
      case Some(value) => pure(value)
      case None => fallbackOnNone
    }
  }

  def foldLeft[E, A, AC](l: Iterable[A])(z: AC)(f: (AC, A) => F[E, AC]): F[E, AC] = {
    def go(l: List[A], ac: AC): F[E, AC] = {
      l match {
        case head :: tail => flatMap(f(ac, head))(go(tail, _))
        case Nil => pure(ac)
      }
    }
    go(l.toList, z)
  }

  def find[E, A](l: Iterable[A])(f: A => F[E, Boolean]): F[E, Option[A]] = {
    def go(l: List[A]): F[E, Option[A]] = {
      l match {
        case head :: tail => flatMap(f(head))(if (_) pure(Some(head)) else go(tail))
        case Nil => pure(None)
      }
    }
    go(l.toList)
  }

  def collectFirst[E, A, B](l: Iterable[A])(f: A => F[E, Option[B]]): F[E, Option[B]] = {
    def go(l: List[A]): F[E, Option[B]] = {
      l match {
        case head :: tail =>
          flatMap(f(head)) {
            case res @ Some(_) => pure(res)
            case None => go(tail)
          }
        case Nil => pure(None)
      }
    }
    go(l.toList)
  }

  /**
    * Execute an action repeatedly until its result fails to satisfy the given predicate
    * and return that result, discarding all others.
    */
  def iterateWhile[E, A](r: F[E, A])(p: A => Boolean): F[E, A] = {
    flatMap(r)(i => iterateWhileF(i)(_ => r)(p))
  }

  /**
    * Execute an action repeatedly until its result satisfies the given predicate
    * and return that result, discarding all others.
    */
  def iterateUntil[E, A](r: F[E, A])(p: A => Boolean): F[E, A] = {
    flatMap(r)(i => iterateUntilF(i)(_ => r)(p))
  }

  /**
    * Apply an effectful function iteratively until its result fails
    * to satisfy the given predicate and return that result.
    */
  def iterateWhileF[E, A](init: A)(f: A => F[E, A])(p: A => Boolean): F[E, A] = {
    tailRecM(init) {
      a =>
        if (p(a)) {
          map(f(a))(Left(_))
        } else {
          pure(Right(a))
        }
    }
  }

  /**
    * Apply an effectful function iteratively until its result satisfies
    * the given predicate and return that result.
    */
  def iterateUntilF[E, A](init: A)(f: A => F[E, A])(p: A => Boolean): F[E, A] = {
    iterateWhileF(init)(f)(!p(_))
  }

  // defaults
  override def map[E, A, B](r: F[E, A])(f: A => B): F[E, B] = flatMap(r)(a => pure(f(a)))
  override def *>[E, A, B](f: F[E, A], next: => F[E, B]): F[E, B] = flatMap(f)(_ => next)
  override def <*[E, A, B](f: F[E, A], next: => F[E, B]): F[E, A] = flatMap(f)(a => map(next)(_ => a))
  override def map2[E, A, B, C](r1: F[E, A], r2: => F[E, B])(f: (A, B) => C): F[E, C] = flatMap(r1)(a => map(r2)(b => f(a, b)))
}
