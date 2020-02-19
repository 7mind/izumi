package izumi.functional.bio

import com.github.ghik.silencer.silent
import zio.{IO, Semaphore}

trait BIOSemaphore[F[_, _]] {
  def acquire: F[Nothing, Unit]
  def release: F[Nothing, Unit]

  def acquireN(n: Long): F[Nothing, Unit]
  def releaseN(n: Long): F[Nothing, Unit]
}

object BIOSemaphore {
  @silent("deprecated")
  def fromZIO(semaphore: Semaphore): BIOSemaphore[IO] =
    new BIOSemaphore[IO] {
      override def acquire: IO[Nothing, Unit] = semaphore.acquire
      override def release: IO[Nothing, Unit] = semaphore.release

      override def acquireN(n: Long): IO[Nothing, Unit] = semaphore.acquireN(n)
      override def releaseN(n: Long): IO[Nothing, Unit] = semaphore.releaseN(n)
    }
}
