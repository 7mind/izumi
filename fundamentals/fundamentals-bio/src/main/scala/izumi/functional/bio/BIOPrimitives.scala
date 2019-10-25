package izumi.functional.bio

import zio.{IO, Promise, Ref, Semaphore}

trait BIOPrimitives[F[_, _]] {
  def mkRef[A](a: A): F[Nothing, BIORef[F, A]]
  def mkLatch[A]: F[Nothing, BIOPromise[F, Nothing, A]]
  def mkPromise[E, A]: F[Nothing, BIOPromise[F, E, A]]
  def mkSemaphore(permits: Long): F[Nothing, BIOSemaphore[F]]
}

object BIOPrimitives {
  def apply[F[_, _]: BIOPrimitives]: BIOPrimitives[F] = implicitly

  implicit val zioPrimitives: BIOPrimitives[IO] = {
    new BIOPrimitives[IO] {
      override def mkRef[A](a: A): IO[Nothing, BIORef[IO, A]]                = Ref.make(a).map(BIORef.fromZIO)
      override def mkLatch[A]: IO[Nothing, BIOPromise[IO, Nothing, A]]       = mkPromise[Nothing, A]
      override def mkPromise[E, A]: IO[Nothing, BIOPromise[IO, E, A]]        = Promise.make[E, A].map(BIOPromise.fromZIO)
      override def mkSemaphore(permits: Long): IO[Nothing, BIOSemaphore[IO]] = Semaphore.make(permits).map(BIOSemaphore.fromZIO)
    }
  }
}
