package izumi.functional.bio.impl

import cats.effect.std.Semaphore
import cats.effect.kernel.{Deferred, GenConcurrent, Ref, Sync}
import izumi.functional.bio.{Async2, Fork2, Primitives2, Promise2, Ref2, Semaphore2, catz}

open class PrimitivesFromBIOAndCats[F[+_, +_]: Async2: Fork2] extends Primitives2[F] {
  private[this] val Concurrent: GenConcurrent[F[Throwable, _], Throwable] = catz.BIOAsyncForkToConcurrent(Async2, Async2, Fork2, this)
  private[this] val Sync: Sync[F[Throwable, _]] = {
    // pass nulls for blocking and clock since Ref.Make.syncInstance only uses the `delay` method of `Sync`
    catz.BIOToSync(Async2, null, null)
  }

  override def mkRef[A](a: A): F[Nothing, Ref2[F, A]] = {
    Ref.of(a)(Ref.Make.syncInstance(Sync)).map(Ref2.fromCats[F, A]).orTerminate
  }
  override def mkPromise[E, A]: F[Nothing, Promise2[F, E, A]] = {
    Deferred.apply[F[Throwable, _], F[E, A]](Concurrent).map(Promise2.fromCats[F, E, A]).orTerminate
  }
  override def mkSemaphore(permits: Long): F[Nothing, Semaphore2[F]] = {
    Semaphore.apply(permits)(Concurrent).map(Semaphore2.fromCats[F]).orTerminate
  }
}
