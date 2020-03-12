package izumi.functional.bio.instances
import cats.~>
import izumi.functional.bio.BIOExit

trait BIOPanic3[F[-_, +_, +_]] extends BIOBracket3[F] with BIOPanicSyntax {
  def terminate(v: => Throwable): F[Any, Nothing, Nothing]
  def sandbox[R, E, A](r: F[R, E, A]): F[R, BIOExit.Failure[E], A]

  @inline final def orTerminate[R, A](r: F[R, Throwable, A]): F[R, Nothing, A] = catchAll(r)(terminate(_))
}

private[bio] sealed trait BIOPanicSyntax
object BIOPanicSyntax {
  implicit final class BIOPanicOrTerminateK[F[-_, +_, +_]](private val F: BIOPanic3[F]) extends AnyVal {
    def orTerminateK[R]: F[R, Throwable, ?] ~> F[R, Nothing, ?] = Lambda[F[R, Throwable, ?] ~> F[R, Nothing, ?]](f => F.orTerminate(f))
  }
}
