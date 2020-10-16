package izumi.functional.bio

import cats.~>

trait Panic3[F[-_, +_, +_]] extends Bracket3[F] with PanicSyntax {
  def terminate(v: => Throwable): F[Any, Nothing, Nothing]
  def sandbox[R, E, A](r: F[R, E, A]): F[R, Exit.Failure[E], A]

  @inline final def orTerminate[R, A](r: F[R, Throwable, A]): F[R, Nothing, A] = catchAll(r)(terminate(_))
}

private[bio] sealed trait PanicSyntax
object PanicSyntax {
  implicit final class PanicOrTerminateK[F[-_, +_, +_]](private val F: Panic3[F]) extends AnyVal {
    def orTerminateK[R]: F[R, Throwable, ?] ~> F[R, Nothing, ?] = {
      Lambda[F[R, Throwable, ?] ~> F[R, Nothing, ?]](f => F.orTerminate(f))
    }
  }
}
