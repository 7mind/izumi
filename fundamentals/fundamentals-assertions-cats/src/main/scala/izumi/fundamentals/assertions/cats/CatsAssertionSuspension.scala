package izumi.fundamentals.assertions.cats

import cats.effect.kernel.Sync
import izumi.fundamentals.assertions.AssertionSuspension1

object CatsAssertionSuspension {
  implicit def fromSync[F[_]](implicit sync: Sync[F]): AssertionSuspension1[F] = new AssertionSuspension1[F] {
    override def suspend(assertion: => Unit): F[Unit] = sync.delay(assertion)
  }
}
