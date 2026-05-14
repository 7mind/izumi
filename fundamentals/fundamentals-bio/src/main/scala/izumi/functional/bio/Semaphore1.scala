package izumi.functional.bio

import cats.effect.std.Semaphore
import izumi.functional.bio.data.~>
import izumi.functional.lifecycle.Lifecycle
import zio.ZIO

trait Semaphore1[+F[_]] {
  def acquire: F[Unit]
  def release: F[Unit]

  def acquireN(n: Long): F[Unit]
  def releaseN(n: Long): F[Unit]
}

object Semaphore1 {

  implicit final class Semaphore1Ops[+F[_]](private val self: Semaphore1[F]) extends AnyVal {
    def mapK[G[_]](fg: F ~> G): Semaphore1[G] = new Semaphore1[G] {
      override def acquire: G[Unit] = fg(self.acquire)
      override def release: G[Unit] = fg(self.release)

      override def acquireN(n: Long): G[Unit] = fg(self.acquireN(n))
      override def releaseN(n: Long): G[Unit] = fg(self.releaseN(n))
    }
  }

  /** Bifunctor semaphore — over a real `F[+_, +_]` rather than via the `Semaphore1` partial-application. */
  trait Semaphore2[F[+_, +_]] extends Semaphore1[F[Nothing, _]] {
    def lifecycle: Lifecycle[F, Nothing, Unit]
  }

  object Semaphore2 {

    implicit final class Semaphore2Ops[F[+_, +_]](private val self: Semaphore2[F]) extends AnyVal {
      def mapK[G[+_, +_]](fg: izumi.functional.bio.data.Morphism2[F, G]): Semaphore2[G] = new Semaphore2[G] {
        // mapK over the monofunctor-projected face uses the Nothing-error Morphism1 derived from fg.
        private val fgMono: izumi.functional.bio.data.Morphism1[F[Nothing, _], G[Nothing, _]] = fg
        override def acquire: G[Nothing, Unit] = fgMono(self.acquire)
        override def release: G[Nothing, Unit] = fgMono(self.release)

        override def acquireN(n: Long): G[Nothing, Unit] = fgMono(self.acquireN(n))
        override def releaseN(n: Long): G[Nothing, Unit] = fgMono(self.releaseN(n))

        override def lifecycle: Lifecycle[G, Nothing, Unit] = self.lifecycle.mapK(fg)
      }
    }

    def fromCats[F[+_, +_]: IO2: Primitives2](semaphore: Semaphore[F[Throwable, _]]): Semaphore2[F] = new Semaphore2[F] {
      override def acquire: F[Nothing, Unit] = assertNoError(semaphore.acquire)
      override def release: F[Nothing, Unit] = assertNoError(semaphore.release)

      override def acquireN(n: Long): F[Nothing, Unit] = assertNoError(semaphore.acquireN(n))
      override def releaseN(n: Long): F[Nothing, Unit] = assertNoError(semaphore.releaseN(n))

      override def lifecycle: Lifecycle[F, Nothing, Unit] = {
        Lifecycle.makeUninterruptibleExcept[F, Nothing, Unit](
          acquire = restore => restore(assertNoError(semaphore.acquire))
        )(release = _ => assertNoError(semaphore.release))
      }

      // prevent Semaphore.acquire from being non-atomic when used with F.uninterruptibleExcept due to added .orTerminate
      private[this] def assertNoError[A](f: F[Throwable, A]): F[Nothing, A] = f.asInstanceOf[F[Nothing, A]]
    }

    def fromZIO(tSemaphore: zio.stm.TSemaphore): Semaphore2[zio.IO] = new Semaphore2[zio.IO] {
      override def acquire: ZIO[Any, Nothing, Unit] = tSemaphore.acquire.commit
      override def release: ZIO[Any, Nothing, Unit] = tSemaphore.release.commit

      override def acquireN(n: Long): ZIO[Any, Nothing, Unit] = tSemaphore.acquireN(n).commit
      override def releaseN(n: Long): ZIO[Any, Nothing, Unit] = tSemaphore.releaseN(n).commit

      override def lifecycle: Lifecycle[zio.IO, Nothing, Unit] = Lifecycle.fromZIO[Any](tSemaphore.withPermitScoped)
    }
  }
}
