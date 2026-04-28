package izumi.functional.bio.syntax

import izumi.functional.bio.ApplicativeError2

import scala.annotation.targetName

trait ApplicativeError2ExtensionMethods {
  extension [F[+_, +_], E, A](r: F[E, A])(using F: ApplicativeError2[F]) {
    @targetName("orElseExt")
    def orElse[E2, A1 >: A](r2: => F[E2, A1]): F[E2, A1] = F.orElse(r, r2)

    @targetName("leftMap2Ext")
    def leftMap2[E2, A1 >: A, E3](r2: => F[E2, A1])(f: (E, E2) => E3): F[E3, A1] = F.leftMap2(r, r2)(f)
  }
}
