package izumi.functional.bio

trait Concurrent3[F[-_, +_, +_]] extends Parallel3[F] {
  override def InnerF: Panic3[F]

  /** Race two actions, the winner is the first action to TERMINATE, whether by success or failure */
  def race[R, E, A](r1: F[R, E, A], r2: F[R, E, A]): F[R, E, A]

  /** Race two actions, the winner is the first action to TERMINATE, whether by success or failure */
  def racePair[R, E, A, B](fa: F[R, E, A], fb: F[R, E, B]): F[R, E, Either[(A, Fiber3[F, E, B]), (Fiber3[F, E, A], B)]]

  def uninterruptible[R, E, A](r: F[R, E, A]): F[R, E, A]

  def yieldNow: F[Any, Nothing, Unit]

  def never: F[Any, Nothing, Nothing]
}
