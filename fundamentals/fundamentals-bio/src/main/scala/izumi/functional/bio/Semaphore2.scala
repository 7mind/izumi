package izumi.functional.bio

import cats.effect.concurrent.Semaphore

trait Semaphore2[+F[_, _]] {
  def acquire: F[Nothing, Unit]
  def release: F[Nothing, Unit]

  def acquireN(n: Long): F[Nothing, Unit]
  def releaseN(n: Long): F[Nothing, Unit]
}

object Semaphore2 {
  def fromCats[F[+_, +_]: Panic2](semaphore: Semaphore[F[Throwable, ?]]): Semaphore2[F] =
    new Semaphore2[F] {
      override def acquire: F[Nothing, Unit] = semaphore.acquire.orTerminate
      override def release: F[Nothing, Unit] = semaphore.release.orTerminate

      override def acquireN(n: Long): F[Nothing, Unit] = semaphore.acquireN(n).orTerminate
      override def releaseN(n: Long): F[Nothing, Unit] = semaphore.releaseN(n).orTerminate
    }
}
