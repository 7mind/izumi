package izumi.functional.bio.impl

import cats.effect.Concurrent
import cats.effect.concurrent.{Deferred, Ref, Semaphore}
import izumi.functional.bio.{BIOAsync, BIOFork, BIOPrimitives, BIOPromise, BIORef, BIOSemaphore, catz}

class BIOPrimitivesCats[F[+_, +_]: BIOAsync: BIOFork] extends BIOPrimitives[F] {
  private val Concurrent: Concurrent[F[Throwable, ?]] = catz.BIOAsyncForkToConcurrent

  override def mkRef[A](a: A): F[Nothing, BIORef[F, A]] = {
    Ref.of(a)(Concurrent).map(BIORef.fromCats[F, A]).orTerminate
  }
  override def mkPromise[E, A]: F[Nothing, BIOPromise[F, E, A]] = {
    Deferred.tryable[F[Throwable, ?], F[E, A]](Concurrent).map(BIOPromise.fromCats[F, E, A]).orTerminate
  }
  override def mkSemaphore(permits: Long): F[Nothing, BIOSemaphore[F]] = {
    Semaphore(permits)(Concurrent).map(BIOSemaphore.fromCats[F]).orTerminate
  }
}
