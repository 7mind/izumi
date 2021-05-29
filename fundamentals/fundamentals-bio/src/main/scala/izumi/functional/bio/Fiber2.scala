package izumi.functional.bio

import izumi.functional.bio.Exit.MonixExit._
import izumi.functional.bio.Exit.ZIOExit
import zio.IO

trait Fiber2[+F[+_, +_], +E, +A] {
  def join: F[E, A]
  def observe: F[Nothing, Exit[E, A]]
  def interrupt: F[Nothing, Unit]
}

object Fiber2 {
  @inline def fromZIO[E, A](f: zio.Fiber[E, A]): Fiber2[IO, E, A] =
    new Fiber2[IO, E, A] {
      override val join: IO[E, A] = f.join
      override val observe: IO[Nothing, Exit[E, A]] = f.await.map(ZIOExit.toExit[E, A])
      override val interrupt: IO[Nothing, Unit] = f.interrupt.void
    }

  @inline def fromMonix[E, A](f: monix.bio.Fiber[E, A]): Fiber2[monix.bio.IO, E, A] =
    new Fiber2[monix.bio.IO, E, A] {
      override val join: monix.bio.IO[E, A] = f.join
      override val observe: monix.bio.IO[Nothing, Exit[E, A]] = f.join.redeemCause(c => toExit(c), a => Exit.Success(a))
      override val interrupt: monix.bio.IO[Nothing, Unit] = f.cancel
    }

  implicit final class ToCats[FR[+_, +_], A](private val bioFiber: Fiber2[FR, Throwable, A]) extends AnyVal {
    def toCats(implicit F: Functor2[FR]): cats.effect.Fiber[FR[Throwable, `?`], A] = new cats.effect.Fiber[FR[Throwable, `?`], A] {
      override def cancel: FR[Throwable, Unit] = F.void(bioFiber.interrupt)
      override def join: FR[Throwable, A] = bioFiber.join
    }
  }
}
