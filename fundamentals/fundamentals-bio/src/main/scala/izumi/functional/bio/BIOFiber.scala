package izumi.functional.bio

import izumi.functional.bio.BIOExit.MonixExit._
import izumi.functional.bio.BIOExit.ZIOExit
import monix.bio
import zio.{Fiber, IO, ZIO}

trait BIOFiber3[F[-_, +_, +_], +E, +A] {
  def join: F[Any, E, A]
  def observe: F[Any, Nothing, BIOExit[E, A]]
  def interrupt: F[Any, Nothing, Unit]
}

object BIOFiber3 {
  @inline def fromZIO[E, A](f: Fiber[E, A]): BIOFiber3[ZIO, E, A] =
    new BIOFiber3[ZIO, E, A] {
      override val join: IO[E, A] = f.join
      override val observe: IO[Nothing, BIOExit[E, A]] = f.await.map(ZIOExit.toBIOExit[E, A])
      override val interrupt: IO[Nothing, Unit] = f.interrupt.void
    }

  implicit final class ToCats[FR[-_, +_, +_], A](private val bioFiber: BIOFiber3[FR, Throwable, A]) extends AnyVal {
    def toCats(implicit F: BIOFunctor3[FR]): cats.effect.Fiber[FR[Any, Throwable, ?], A] = new cats.effect.Fiber[FR[Any, Throwable, ?], A] {
      override def cancel: FR[Any, Throwable, Unit] = F.void(bioFiber.interrupt)

      override def join: FR[Any, Throwable, A] = bioFiber.join
    }
  }
}

object BIOFiber {
  @inline def fromZIO[E, A](f: Fiber[E, A]): BIOFiber3[ZIO, E, A] = BIOFiber3.fromZIO(f)
  @inline def fromMonix[E, A](f: bio.Fiber[E, A]): BIOFiber[bio.IO, E, A] =
    new BIOFiber[bio.IO, E, A] {
      override val join: bio.IO[E, A] = f.join
      override val observe: bio.IO[Nothing, BIOExit[E, A]] = f.join.redeemCause(c => fromMonixCause(c), a => BIOExit.Success(a))
      override val interrupt: bio.IO[Nothing, Unit] = f.cancel.void
    }
}
