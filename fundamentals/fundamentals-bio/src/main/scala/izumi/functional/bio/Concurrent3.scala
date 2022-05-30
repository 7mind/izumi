package izumi.functional.bio

trait Concurrent3[F[-_, +_, +_]] extends Parallel3[F] {
  override def InnerF: Panic3[F]

  /** Race two actions, the winner is the first action to TERMINATE, whether by success or failure */
  def race[R, E, A](r1: F[R, E, A], r2: F[R, E, A]): F[R, E, A]

  /**
    * Race two actions, the winner is the first action to TERMINATE, whether by success or failure
    *
    * Unlike [[race]], the loser is not interrupted after the winner has terminated - whether by success or failure.
    */
  def racePairUnsafe[R, E, A, B](fa: F[R, E, A], fb: F[R, E, B]): F[R, E, Either[(Exit[E, A], Fiber3[F, E, B]), (Fiber3[F, E, A], Exit[E, B])]]

  def yieldNow: F[Any, Nothing, Unit]

  def never: F[Any, Nothing, Nothing]
}
