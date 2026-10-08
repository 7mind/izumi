package izumi.fundamentals.assertions.bio

import izumi.functional.bio.IO2
import izumi.fundamentals.assertions.AssertionSuspension2

object BIOAssertionSuspension {
  implicit def fromIO2[F[+_, +_]](implicit io: IO2[F]): AssertionSuspension2[F] = new AssertionSuspension2[F] {
    override def suspend(assertion: => Unit): F[Nothing, Unit] = io.sync(assertion)
  }
}
