package izumi.functional.bio

import cats.~>
import izumi.functional.bio.data.Morphism3

trait Panic3[F[-_, +_, +_]] extends Bracket3[F] with PanicSyntax {
  def terminate(v: => Throwable): F[Any, Nothing, Nothing]
  def halt[E, A](exit: Exit.Failure[E]): F[Any, E, Nothing]

  def sandbox[R, E, A](r: F[R, E, A]): F[R, Exit.Failure[E], A]

  def sendInterruptToSelf: F[Any, Nothing, Unit]

  def uninterruptible[R, E, A](r: F[R, E, A]): F[R, E, A]
  def uninterruptibleWith[R, E, A](r: Morphism3[F, F] => F[R, E, A]): F[R, E, A]

  @inline final def orTerminate[R, A](r: F[R, Throwable, A]): F[R, Nothing, A] = catchAll(r)(terminate(_))
}

private[bio] sealed trait PanicSyntax
object PanicSyntax {
  implicit final class PanicOrTerminateK[F[-_, +_, +_]](private val F: Panic3[F]) extends AnyVal {
    def orTerminateK[R]: F[R, Throwable, _] ~> F[R, Nothing, _] = {
      Lambda[F[R, Throwable, _] ~> F[R, Nothing, _]](f => F.orTerminate(f))
    }
  }
}
