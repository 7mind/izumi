package izumi.functional.bio

trait BIOBracket3[F[-_, +_, +_]] extends BIOMonadError3[F] {
  def bracketCase[R, E, A, B](acquire: F[R, E, A])(release: (A, BIOExit[E, B]) => F[R, Nothing, Unit])(use: A => F[R, E, B]): F[R, E, B]

  def bracket[R, E, A, B](acquire: F[R, E, A])(release: A => F[R, Nothing, Unit])(use: A => F[R, E, B]): F[R, E, B] = {
    bracketCase[R, E, A, B](acquire)((a, _) => release(a))(use)
  }

  // defaults
  override def guarantee[R, E, A](f: F[R, E, A], cleanup: F[R, Nothing, Unit]): F[R, E, A] = {
    bracket[R, E, Unit, A](unit)(_ => cleanup)(_ => f)
  }
}
