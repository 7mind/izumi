package izumi.functional.bio.impl

import cats.effect.concurrent.Semaphore
import izumi.functional.bio.{BIOPrimitives, BIOPromise, BIORef, BIOSemaphore, catz}
import zio.{IO, Promise, Ref, Task}

object BIOPrimitivesZio extends BIOPrimitivesZio

class BIOPrimitivesZio extends BIOPrimitives[IO] {
  override def mkRef[A](a: A): IO[Nothing, BIORef[IO, A]] = {
    Ref.make(a).map(BIORef.fromZIO)
  }
  override def mkPromise[E, A]: IO[Nothing, BIOPromise[IO, E, A]] = {
    Promise.make[E, A].map(BIOPromise.fromZIO)
  }
  override def mkSemaphore(permits: Long): IO[Nothing, BIOSemaphore[IO]] = {
    Semaphore[Task](permits)(catz.BIOAsyncForkToConcurrent[IO])
      .map(BIOSemaphore.fromCats[IO])
      .orTerminate
  }
}
