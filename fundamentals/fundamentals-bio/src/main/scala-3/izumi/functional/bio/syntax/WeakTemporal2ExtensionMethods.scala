package izumi.functional.bio.syntax

import izumi.functional.bio.WeakTemporal2

import scala.annotation.targetName
import scala.concurrent.duration.FiniteDuration

trait WeakTemporal2ExtensionMethods {
  extension [F[+_, +_], E, A](r: F[E, A])(using F: WeakTemporal2[F]) {
    @targetName("repeatUntilExt")
    def repeatUntil[E2 >: E, A2](tooManyAttemptsError: => E2, sleep: FiniteDuration, maxAttempts: Int)(using ev: A <:< Option[A2]): F[E2, A2] =
      F.repeatUntil[E2, A2](r.asInstanceOf[F[E2, Option[A2]]])(tooManyAttemptsError, sleep, maxAttempts)
  }
}
