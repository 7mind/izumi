package izumi.functional.bio

trait Concurrent2[F[+_, +_]] extends Parallel2[F] {
  override def InnerF: Panic2[F]

  /** Race two actions, the winner is the first action to TERMINATE, whether by success or failure */
  def race[E, A](r1: F[E, A], r2: F[E, A]): F[E, A]

  /**
    * Race two actions, the winner is the first action to TERMINATE, whether by success or failure
    *
    * Unlike [[race]], the loser is not interrupted after the winner has terminated - whether by success or failure.
    */
  def racePairUnsafe[E, A, B](fa: F[E, A], fb: F[E, B]): F[E, Either[(Exit[E, A], Fiber2[F, E, B]), (Fiber2[F, E, A], Exit[E, B])]]

  def yieldNow: F[Nothing, Unit]

  def never: F[Nothing, Nothing]
}
