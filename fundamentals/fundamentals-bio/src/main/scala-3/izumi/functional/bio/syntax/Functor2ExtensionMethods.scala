package izumi.functional.bio.syntax

import izumi.functional.bio.Functor2

import scala.annotation.{targetName, unused}

trait Functor2ExtensionMethods {
  extension [F[+_, +_], E, A](r: F[E, A])(using F: Functor2[F]) {
    @targetName("mapExt")
    def map[B](f: A => B): F[E, B] = F.map(r)(f)

    @targetName("asExt")
    infix def as[B](b: => B): F[E, B] = F.map(r)(_ => b)

    @targetName("voidExt")
    def void: F[E, Unit] = F.void(r)

    @targetName("widenExt")
    def widen[A1](using @unused ev: A <:< A1): F[E, A1] = r.asInstanceOf[F[E, A1]]

    @targetName("fromOptionOrExt")
    def fromOptionOr[B, C](valueOnNone: => C)(using @unused ev: A <:< Option[B], ev2: C <:< B): F[E, B] =
      F.fromOptionOr(ev2(valueOnNone), r.asInstanceOf[F[E, Option[B]]])
  }
}
