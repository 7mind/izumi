package izumi.functional.bio.syntax

import izumi.functional.bio.{Concurrent2, Exit, Fiber2}

import scala.annotation.targetName

trait Concurrent2ExtensionMethods {
  extension [F[+_, +_], E, A](r: F[E, A])(using F: Concurrent2[F]) {
    @targetName("raceExt")
    infix def race[E1 >: E, A1 >: A](that: F[E1, A1]): F[E1, A1] = F.race(r.asInstanceOf[F[E1, A1]], that)

    @targetName("racePairUnsafeExt")
    def racePairUnsafe[E1 >: E, A1 >: A](
      that: F[E1, A1]
    ): F[E1, Either[(Exit[E1, A], Fiber2[F, E1, A1]), (Fiber2[F, E1, A], Exit[E1, A1])]] = F.racePairUnsafe(r, that)
  }
}
