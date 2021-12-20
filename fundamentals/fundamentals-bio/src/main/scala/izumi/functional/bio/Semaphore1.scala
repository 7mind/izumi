package izumi.functional.bio

import izumi.functional.bio.data.~>
import zio.ZIO
import zio.stm.{USTM, ZSTM}
import cats.effect.std.Semaphore

trait Semaphore1[+F[_]] {
  def acquire: F[Unit]
  def release: F[Unit]

  def acquireN(n: Long): F[Unit]
  def releaseN(n: Long): F[Unit]
}

object Semaphore1 {
  def fromCats[F[+_, +_]: Panic2](semaphore: Semaphore[F[Throwable, _]]): Semaphore2[F] = new Semaphore2[F] {
    override def acquire: F[Nothing, Unit] = semaphore.acquire.orTerminate
    override def release: F[Nothing, Unit] = semaphore.release.orTerminate

    override def acquireN(n: Long): F[Nothing, Unit] = semaphore.acquireN(n).orTerminate
    override def releaseN(n: Long): F[Nothing, Unit] = semaphore.releaseN(n).orTerminate
  }

  def fromZIO(tSemaphore: zio.stm.TSemaphore): Semaphore3[ZIO] = new Semaphore3[ZIO] {
    override def acquire: ZIO[Any, Nothing, Unit] = tSemaphore.acquire.commit
    override def release: ZIO[Any, Nothing, Unit] = tSemaphore.release.commit
    override def acquireN(n: Long): ZIO[Any, Nothing, Unit] = tSemaphore.acquireN(n).commit
    override def releaseN(n: Long): ZIO[Any, Nothing, Unit] = tSemaphore.releaseN(n).commit
  }

  def fromSTM(tSemaphore: zio.stm.TSemaphore): Semaphore3[ZSTM] = new Semaphore3[ZSTM] {
    override def acquire: USTM[Unit] = tSemaphore.acquire
    override def release: USTM[Unit] = tSemaphore.release
    override def acquireN(n: Long): USTM[Unit] = tSemaphore.acquireN(n)
    override def releaseN(n: Long): USTM[Unit] = tSemaphore.releaseN(n)
  }

  implicit final class Semaphore1Ops[+F[_]](private val self: Semaphore1[F]) extends AnyVal {
    def mapK[G[_]](fg: F ~> G): Semaphore1[G] = new Semaphore1[G] {
      override def acquire: G[Unit] = fg(self.acquire)
      override def release: G[Unit] = fg(self.release)
      override def acquireN(n: Long): G[Unit] = fg(self.acquireN(n))
      override def releaseN(n: Long): G[Unit] = fg(self.releaseN(n))
    }
  }
}
