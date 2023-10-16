package izumi.functional.bio

import scala.annotation.unused

trait Monad3[F[-_, +_, +_]] extends Applicative3[F] {
  def flatMap[R, E, A, B](r: F[R, E, A])(f: A => F[R, E, B]): F[R, E, B]
  def flatten[R, E, A](r: F[R, E, F[R, E, A]]): F[R, E, A] = flatMap(r)(identity)

  def tailRecM[R, E, A, B](a: A)(f: A => F[R, E, Either[A, B]]): F[R, E, B] = {
    def loop(a: A): F[R, E, B] = flatMap(f(a)) {
      case Left(next) => loop(next)
      case Right(res) => pure(res)
    }

    flatMap[R, E, Unit, B](unit)(_ => loop(a)) // https://github.com/zio/interop-cats/pull/460
  }

  def tap[R, E, A](r: F[R, E, A], f: A => F[R, E, Unit]): F[R, E, A] = flatMap(r)(a => as(f(a))(a))

  @inline final def when[R, E, E1](cond: F[R, E, Boolean])(ifTrue: => F[R, E1, Unit])(implicit ev: E <:< E1): F[R, E1, Unit] = {
    ifThenElse(cond)(ifTrue, unit)
  }
  @inline final def unless[R, E, E1](cond: F[R, E, Boolean])(ifFalse: => F[R, E1, Unit])(implicit ev: E <:< E1): F[R, E1, Unit] = {
    ifThenElse(cond)(unit, ifFalse)
  }
  @inline final def ifThenElse[R, E, E1, A](
    cond: F[R, E, Boolean]
  )(ifTrue: => F[R, E1, A],
    ifFalse: => F[R, E1, A],
  )(implicit @unused ev: E <:< E1
  ): F[R, E1, A] = {
    flatMap(cond.asInstanceOf[F[R, E1, Boolean]])(if (_) ifTrue else ifFalse)
  }

  /** Extracts the optional value, or executes the `fallbackOnNone` effect */
  def fromOptionF[R, E, A](fallbackOnNone: => F[R, E, A], r: F[R, E, Option[A]]): F[R, E, A] = {
    flatMap(r) {
      case Some(value) => pure(value)
      case None => fallbackOnNone
    }
  }

  def foldLeft[R, E, A, AC](l: Iterable[A])(z: AC)(f: (AC, A) => F[R, E, AC]): F[R, E, AC] = {
    def go(l: List[A], ac: AC): F[R, E, AC] = {
      l match {
        case head :: tail => flatMap(f(ac, head))(go(tail, _))
        case Nil => pure(ac)
      }
    }
    go(l.toList, z)
  }

  def find[R, E, A](l: Iterable[A])(f: A => F[R, E, Boolean]): F[R, E, Option[A]] = {
    def go(l: List[A]): F[R, E, Option[A]] = {
      l match {
        case head :: tail => flatMap(f(head))(if (_) pure(Some(head)) else go(tail))
        case Nil => pure(None)
      }
    }
    go(l.toList)
  }

  def collectFirst[R, E, A, B](l: Iterable[A])(f: A => F[R, E, Option[B]]): F[R, E, Option[B]] = {
    def go(l: List[A]): F[R, E, Option[B]] = {
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
  def iterateWhile[R, E, A](r: F[R, E, A])(p: A => Boolean): F[R, E, A] = {
    flatMap(r)(i => iterateWhileF(i)(_ => r)(p))
  }

  /**
    * Execute an action repeatedly until its result satisfies the given predicate
    * and return that result, discarding all others.
    */
  def iterateUntil[R, E, A](r: F[R, E, A])(p: A => Boolean): F[R, E, A] = {
    flatMap(r)(i => iterateUntilF(i)(_ => r)(p))
  }

  /**
    * Apply an effectful function iteratively until its result fails
    * to satisfy the given predicate and return that result.
    */
  def iterateWhileF[R, E, A](init: A)(f: A => F[R, E, A])(p: A => Boolean): F[R, E, A] = {
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
  def iterateUntilF[R, E, A](init: A)(f: A => F[R, E, A])(p: A => Boolean): F[R, E, A] = {
    iterateWhileF(init)(f)(!p(_))
  }

  // defaults
  override def map[R, E, A, B](r: F[R, E, A])(f: A => B): F[R, E, B] = flatMap(r)(a => pure(f(a)))
  override def *>[R, E, A, B](f: F[R, E, A], next: => F[R, E, B]): F[R, E, B] = flatMap(f)(_ => next)
  override def <*[R, E, A, B](f: F[R, E, A], next: => F[R, E, B]): F[R, E, A] = flatMap(f)(a => map(next)(_ => a))
  override def map2[R, E, A, B, C](r1: F[R, E, A], r2: => F[R, E, B])(f: (A, B) => C): F[R, E, C] = flatMap(r1)(a => map(r2)(b => f(a, b)))
}
