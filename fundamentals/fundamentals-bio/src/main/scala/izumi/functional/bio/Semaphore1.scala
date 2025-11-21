package izumi.functional.bio

import cats.effect.kernel.Sync
import cats.effect.std.Semaphore
import izumi.functional.bio.data.~>
import izumi.functional.lifecycle.Lifecycle
import zio.ZIO

trait Semaphore1[+F[_]] {
  def acquire: F[Unit]
  def release: F[Unit]

  def acquireN(n: Long): F[Unit]
  def releaseN(n: Long): F[Unit]

  def lifecycle: Lifecycle[F, Unit]
}

object Semaphore1 {
  def fromCatsNative[F[_]](semaphore: Semaphore[F])(implicit F: Sync[F]): Semaphore1[F] = new Semaphore1[F] {
    override def acquire: F[Unit] = semaphore.acquire
    override def release: F[Unit] = semaphore.release

    override def acquireN(n: Long): F[Unit] = semaphore.acquireN(n)
    override def releaseN(n: Long): F[Unit] = semaphore.releaseN(n)

    override def lifecycle: Lifecycle[F, Unit] = Lifecycle.fromCats(semaphore.permit)
  }

  def fromCats[F[+_, +_]: IO2](semaphore: Semaphore[F[Throwable, _]]): Semaphore2[F] = new Semaphore2[F] {
    override def acquire: F[Nothing, Unit] = assertNoError(semaphore.acquire)
    override def release: F[Nothing, Unit] = assertNoError(semaphore.release)

    override def acquireN(n: Long): F[Nothing, Unit] = assertNoError(semaphore.acquireN(n))
    override def releaseN(n: Long): F[Nothing, Unit] = assertNoError(semaphore.releaseN(n))

    override def lifecycle: Lifecycle[F[Nothing, _], Unit] = {
      Lifecycle.makeUninterruptibleExcept[F[Nothing, _], Unit](
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

    override def lifecycle: Lifecycle[ZIO[Any, Nothing, _], Unit] = Lifecycle.fromZIO(tSemaphore.withPermitScoped)
  }

  implicit final class Semaphore1Ops[+F[_]](private val self: Semaphore1[F]) extends AnyVal {
    def mapK[G[_]](fg: F ~> G): Semaphore1[G] = new Semaphore1[G] {
      override def acquire: G[Unit] = fg(self.acquire)
      override def release: G[Unit] = fg(self.release)

      override def acquireN(n: Long): G[Unit] = fg(self.acquireN(n))
      override def releaseN(n: Long): G[Unit] = fg(self.releaseN(n))

      override def lifecycle: Lifecycle[G, Unit] = ???
    }
  }
}
