package izumi.functional.bio.impl

import cats.effect.Concurrent
import izumi.functional.bio.{Async2, Fork2, Primitives2, Promise2, Ref2, Semaphore2, catz}
import cats.effect.{ Deferred, Ref }
import cats.effect.std.Semaphore

class PrimitivesFromBIOAndCats[F[+_, +_]: Async2: Fork2] extends Primitives2[F] {
  private val Concurrent: Concurrent[F[Throwable, ?]] = catz.BIOAsyncForkToConcurrent

  override def mkRef[A](a: A): F[Nothing, Ref2[F, A]] = {
    Ref.of(a)(Concurrent).map(Ref2.fromCats[F, A]).orTerminate
  }
  override def mkPromise[E, A]: F[Nothing, Promise2[F, E, A]] = {
    Deferred.tryable[F[Throwable, ?], F[E, A]](Concurrent).map(Promise2.fromCats[F, E, A]).orTerminate
  }
  override def mkSemaphore(permits: Long): F[Nothing, Semaphore2[F]] = {
    Semaphore(permits)(Concurrent).map(Semaphore2.fromCats[F]).orTerminate
  }
}
