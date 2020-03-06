package izumi.functional.bio

import cats.effect.concurrent.Semaphore

trait BIOSemaphore[+F[_, _]] {
  def acquire: F[Nothing, Unit]
  def release: F[Nothing, Unit]

  def acquireN(n: Long): F[Nothing, Unit]
  def releaseN(n: Long): F[Nothing, Unit]
}

object BIOSemaphore {
  def fromCats[F[+_, +_]: BIOPanic](semaphore: Semaphore[F[Throwable, ?]]): BIOSemaphore[F] =
    new BIOSemaphore[F] {
      override def acquire: F[Nothing, Unit] = semaphore.acquire.orTerminate
      override def release: F[Nothing, Unit] = semaphore.release.orTerminate

      override def acquireN(n: Long): F[Nothing, Unit] = semaphore.acquireN(n).orTerminate
      override def releaseN(n: Long): F[Nothing, Unit] = semaphore.releaseN(n).orTerminate
    }
}
