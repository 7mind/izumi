package izumi.fundamentals.assertions

/** Construction does not evaluate the assertion. Each execution evaluates it once and raises thrown failures in the effect. */
trait AssertionSuspension1[F[_]] {
  def suspend(assertion: => Unit): F[Unit]
}

/** Construction does not evaluate the assertion. Each execution evaluates it once; thrown failures are defects, never typed errors. */
trait AssertionSuspension2[F[_, _]] {
  def suspend(assertion: => Unit): F[Nothing, Unit]
}
