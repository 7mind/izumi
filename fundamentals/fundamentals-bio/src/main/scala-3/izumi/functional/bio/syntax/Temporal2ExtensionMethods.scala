package izumi.functional.bio.syntax

import izumi.functional.bio.Temporal2

import scala.annotation.targetName
import scala.concurrent.duration.Duration

trait Temporal2ExtensionMethods {
  extension [F[+_, +_], E, A](r: F[E, A])(using F: Temporal2[F]) {
    @targetName("timeoutExt")
    def timeout(duration: Duration): F[E, Option[A]] = F.timeout(duration)(r)

    @targetName("timeoutFailExt")
    def timeoutFail[E1 >: E](e: => E1)(duration: Duration): F[E1, A] = F.timeoutFail(duration)(e, r.asInstanceOf[F[E1, A]])
  }
}
