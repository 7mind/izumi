package izumi.functional.bio.impl

import cats.effect.concurrent.Semaphore
import izumi.functional.bio.{Primitives2, Promise2, Ref2, Semaphore2, catz}
import zio._

object PrimitivesZio extends PrimitivesZio

class PrimitivesZio extends Primitives2[IO] {
  override def mkRef[A](a: A): IO[Nothing, Ref2[IO, A]] = {
    Ref.make(a).map(Ref2.fromZIO)
  }
  override def mkPromise[E, A]: IO[Nothing, Promise2[IO, E, A]] = {
    Promise.make[E, A].map(Promise2.fromZIO)
  }
  override def mkSemaphore(permits: Long): IO[Nothing, Semaphore2[IO]] = {
    PrimitivesZIOCatsSemaphore.mkSemaphore(permits)
  }
}

// zio.Semaphore is currently incompatible with `BIOSemaphore` interface
private[impl] object PrimitivesZIOCatsSemaphore {
  def mkSemaphore(permits: Long): UIO[Semaphore2[IO]] = {
    Semaphore[Task](permits)(catz.BIOAsyncForkToConcurrent[IO])
      .map(Semaphore2.fromCats[IO])
      .orTerminate
  }
}
