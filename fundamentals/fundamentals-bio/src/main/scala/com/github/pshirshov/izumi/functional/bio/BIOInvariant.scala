package com.github.pshirshov.izumi.functional.bio

import scala.util.Try

trait BIOInvariant[R[_, _]] {
  type Or[E, V] = R[E, V]
  type Just[V] = R[Nothing, V]

  @inline def map[E, A, B](r: R[E, A])(f: A => B): R[E, B]

  @inline def redeem[E, A, E2, B](r: R[E, A])(err: E => R[E2, B], succ: A => R[E2, B]): R[E2, B]

  @inline def syncThrowable[A](effect: => A): R[Throwable, A]

  @inline def flatMap[E, A, E1 >: E, B](r: R[E, A])(f0: A => R[E1, B]): R[E1, B]

  @inline def void[E, A](r: R[E, A]): R[E, Unit]

  @inline def leftMap[E, A, E2](r: R[E, A])(f: E => E2): R[E2, A]

  @inline def bimap[E, A, E2, B](r: R[E, A])(f: E => E2, g: A => B): R[E2, B]

  @inline def fromEither[E, V](v: => Either[E, V]): R[E, V]

  @inline def sync[A](effect: => A): R[Nothing, A]

  @inline def point[V](v: => V): R[Nothing, V]

  @inline def fail[E](v: => E): R[E, Nothing]

  @inline def terminate(v: => Throwable): R[Nothing, Nothing]

  @inline def now[A](a: A): R[Nothing, A]

  @inline def unit: R[Nothing, Unit] = now(())

  @inline def bracket[E, A, B](acquire: R[E, A])(release: A => R[Nothing, Unit])(use: A => R[E, B]): R[E, B]

  @inline def sandboxWith[E, A, E2, B](r: R[E, A])(f: R[BIOExit.Failure[E], A] => R[BIOExit.Failure[E2], B]): R[E2, B]

  @inline def sandbox[E, A](r: R[E, A]): R[BIOExit.Failure[E], A]

  @inline def widen[E, A, E1 >: E, A1 >: A](r: R[E, A]): R[E1, A1]

  @inline final def when[E](p: Boolean)(r: R[E, Unit]): R[E, Unit] = {
    if (p) r else widen(unit)
  }

  @inline def traverse[E, A, B](l: Iterable[A])(f: A => R[E, B]): R[E, List[B]]

  @inline final def traverse_[E, A, B](l: Iterable[A])(f: A => R[E, B]): R[E, Unit] = void(traverse(l)(f))

  @inline final def sequence[E, A, B](l: Iterable[R[E, A]]): R[E, List[A]] = traverse(l)(identity)

  @inline final def sequence_[E](l: Iterable[R[E, Unit]]): R[E, Unit] = void(traverse(l)(identity))

  @inline final def fromOption[E, A](errorOnNone: E)(effect: => Option[A]): R[E, A] = flatMap(sync(effect))(e => fromEither(e.toRight(errorOnNone)))

  @inline final def fromOption[A](effect: => Option[A]): R[Unit, A] = flatMap(sync(effect))(e => fromEither(e.toRight(())))

  @inline final def fromTry[A](effect: => Try[A]): R[Throwable, A] = syncThrowable(effect.get)

  @inline def leftFlatMap[E, A, E2](r: R[E, A])(f: E => R[Nothing, E2]): R[E2, A]

  @inline def flip[E, A](r: R[E, A]) : R[A, E]
}

object BIOInvariant {
  def apply[R[_, _]: BIOInvariant]: BIOInvariant[R] = implicitly
}
