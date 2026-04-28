package izumi.functional.bio.syntax

import izumi.functional.bio.Monad2

import scala.annotation.{targetName, unused}

trait Monad2ExtensionMethods {
  extension [F[+_, +_], E, A](r: F[E, A])(using F: Monad2[F]) {
    @targetName("flatMapExt")
    def flatMap[E1 >: E, B](f0: A => F[E1, B]): F[E1, B] = F.flatMap[E1, A, B](r.asInstanceOf[F[E1, A]])(f0)

    @targetName("tapMonadExt")
    def tap[E1 >: E](f0: A => F[E1, Unit]): F[E1, A] = F.tap(r.asInstanceOf[F[E1, A]], f0)

    @targetName("flattenExt")
    def flatten[E1 >: E, A1](using ev: A <:< F[E1, A1]): F[E1, A1] =
      F.flatten(r.asInstanceOf[F[E1, F[E1, A1]]])

    @targetName("iterateWhileMonadExt")
    def iterateWhile(p: A => Boolean): F[E, A] = F.iterateWhile(r)(p)

    @targetName("iterateUntilMonadExt")
    def iterateUntil(p: A => Boolean): F[E, A] = F.iterateUntil(r)(p)

    @targetName("fromOptionFMonadExt")
    def fromOptionF[E1 >: E, B, C](fallbackOnNone: => F[E1, C])(using @unused ev: A <:< Option[B], ev2: C <:< B): F[E1, B] =
      F.fromOptionF[E1, B](fallbackOnNone.asInstanceOf[F[E1, B]], r.asInstanceOf[F[E1, Option[B]]])
  }
}
