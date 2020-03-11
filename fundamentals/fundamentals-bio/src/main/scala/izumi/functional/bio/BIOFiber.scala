package izumi.functional.bio

import izumi.functional.bio.BIOExit.ZIOExit
import zio.{Fiber, ZIO}

trait BIOFiber3[F[-_, +_, +_], -R, +E, +A] {
  def join: F[R, E, A]
  def observe: F[R, Nothing, BIOExit[E, A]]
  def interrupt: F[R, Nothing, BIOExit[E, A]]
}

object BIOFiber {
  def fromZIO[R, E, A](f: Fiber[E, A]): BIOFiber3[ZIO[-?, +?, +?], R, E, A] =
    new BIOFiber3[ZIO[-?, +?, +?], R, E, A] {
      override val join: ZIO[R, E, A] = f.join
      override val observe: ZIO[R, Nothing, BIOExit[E, A]] = f.await.map(ZIOExit.toBIOExit[E, A])
      override def interrupt: ZIO[R, Nothing, BIOExit[E, A]] = f.interrupt.map(ZIOExit.toBIOExit[E, A])
    }

  implicit final class ToCats[F[+_, +_], A](private val bioFiber: BIOFiber[F, Throwable, A]) extends AnyVal {
    def toCats(implicit F: BIOFunctor[F]): cats.effect.Fiber[F[Throwable, ?], A] = new cats.effect.Fiber[F[Throwable, ?], A] {
      override def cancel: F[Throwable, Unit] = F.void(bioFiber.interrupt)
      override def join: F[Throwable, A] = bioFiber.join
    }
  }
}
