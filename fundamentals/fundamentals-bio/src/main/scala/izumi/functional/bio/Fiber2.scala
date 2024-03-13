package izumi.functional.bio

import cats.effect.kernel.Outcome
import izumi.functional.bio.Exit.{CatsExit, ZIOExit}
import zio.IO
//import zio.stacktracer.TracingImplicits.disableAutoTrace

trait Fiber2[+F[+_, +_], +E, +A] {
  def join: F[E, A]
  def observe: F[Nothing, Exit[E, A]]
  def interrupt: F[Nothing, Unit]
}

object Fiber2 {
  @inline def fromZIO[E, A](confirmExternalInterrupt: IO[Nothing, Boolean])(f: zio.Fiber[E, A]): Fiber2[IO, E, A] =
    new Fiber2[IO, E, A] {
      override val join: IO[E, A] = f.join
      override val observe: IO[Nothing, Exit[E, A]] = f.await.flatMap(confirmExternalInterrupt map ZIOExit.toExit[E, A](_))
      override val interrupt: IO[Nothing, Unit] = f.interrupt.void
    }

//  @inline def fromMonix[E, A](f: monix.bio.Fiber[E, A]): Fiber2[monix.bio.IO, E, A] =
//    new Fiber2[monix.bio.IO, E, A] {
//      override val join: monix.bio.IO[E, A] = f.join
//      override val observe: monix.bio.IO[Nothing, Exit[E, A]] = f.join.redeemCause(c => toExit(c), a => Exit.Success(a))
//      override val interrupt: monix.bio.IO[Nothing, Unit] = f.cancel
//    }

  implicit final class ToCats[F[+_, +_], A](private val bioFiber: Fiber2[F, Throwable, A]) extends AnyVal {
    def toCats(implicit F: Applicative2[F]): cats.effect.Fiber[F[Throwable, _], Throwable, A] = new cats.effect.Fiber[F[Throwable, _], Throwable, A] {
      override def cancel: F[Nothing, Unit] = bioFiber.interrupt
      override def join: F[Nothing, Outcome[F[Throwable, _], Throwable, A]] = bioFiber.observe.map(CatsExit.exitToOutcomeThrowable(_))
    }
  }
}
