package izumi.functional.bio.instances

import cats.effect.concurrent.Semaphore
import izumi.functional.bio.{BIOPrimitives, BIOPromise, BIORef, BIOSemaphore, catz}
import zio.{IO, Promise, Ref, Task, ZIO}

trait BIOPrimitives3[FR[-_, +_, +_]] extends BIOPrimitivesInstances {
  type F[+E, +A] = FR[Any, E, A]
  def mkRef[A](a: A): FR[Any, Nothing, BIORef[F, A]]
  def mkLatch: FR[Any, Nothing, BIOPromise[F, Nothing, Unit]]
  def mkPromise[E, A]: FR[Any, Nothing, BIOPromise[F, E, A]]
  def mkSemaphore(permits: Long): FR[Any, Nothing, BIOSemaphore[F]]
}

private[bio] sealed trait BIOPrimitivesInstances
object BIOPrimitivesInstances {
  implicit def BIOPrimitivesZioIO[R]: BIOPrimitives[ZIO[R, +?, +?]] = BIOPrimitivesZio.asInstanceOf[BIOPrimitives[ZIO[R, +?, +?]]]

  implicit object BIOPrimitivesZio extends BIOPrimitives3[ZIO] {
    override def mkRef[A](a: A): IO[Nothing, BIORef[IO, A]] = Ref.make(a).map(BIORef.fromZIO)
    override def mkLatch: IO[Nothing, BIOPromise[IO, Nothing, Unit]] = mkPromise[Nothing, Unit]
    override def mkPromise[E, A]: IO[Nothing, BIOPromise[IO, E, A]] = Promise.make[E, A].map(BIOPromise.fromZIO)
    override def mkSemaphore(permits: Long): IO[Nothing, BIOSemaphore[IO]] = {
      Semaphore[Task](permits)(catz.BIOAsyncForkToConcurrent[IO])
        .map(BIOSemaphore.fromCats[IO])
        .orTerminate
    }
  }
}
