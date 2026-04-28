package izumi.functional.bio.syntax

import izumi.functional.bio.Parallel2

import scala.annotation.targetName

trait Parallel2ExtensionMethods {
  extension [F[+_, +_], E, A](r: F[E, A])(using F: Parallel2[F]) {
    @targetName("zipWithParExt")
    def zipWithPar[E1 >: E, B, C](that: F[E1, B])(f: (A, B) => C): F[E1, C] = F.zipWithPar(r, that)(f)

    @targetName("zipParExt")
    infix def zipPar[E1 >: E, B](that: F[E1, B]): F[E1, (A, B)] = F.zipPar(r, that)

    @targetName("zipParLeftExt")
    infix def zipParLeft[E1 >: E, B](that: F[E1, B]): F[E1, A] = F.zipParLeft(r, that)

    @targetName("zipParRightExt")
    infix def zipParRight[E1 >: E, B](that: F[E1, B]): F[E1, B] = F.zipParRight(r, that)
  }
}
