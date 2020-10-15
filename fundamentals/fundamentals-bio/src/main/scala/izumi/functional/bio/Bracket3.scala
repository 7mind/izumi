package izumi.functional.bio

trait Bracket3[F[-_, +_, +_]] extends Error3[F] {
  def bracketCase[R, E, A, B](acquire: F[R, E, A])(release: (A, Exit[E, B]) => F[R, Nothing, Unit])(use: A => F[R, E, B]): F[R, E, B]

  def bracket[R, E, A, B](acquire: F[R, E, A])(release: A => F[R, Nothing, Unit])(use: A => F[R, E, B]): F[R, E, B] = {
    bracketCase(acquire)((a, _: Exit[E, B]) => release(a))(use)
  }

  def guaranteeCase[R, E, A](f: F[R, E, A], cleanup: Exit[E, A] => F[R, Nothing, Unit]): F[R, E, A] = {
    bracketCase(unit: F[R, E, Unit])((_, e: Exit[E, A]) => cleanup(e))(_ => f)
  }

  // defaults
  override def guarantee[R, E, A](f: F[R, E, A], cleanup: F[R, Nothing, Unit]): F[R, E, A] = {
    bracket(unit: F[R, E, Unit])(_ => cleanup)(_ => f)
  }
}
