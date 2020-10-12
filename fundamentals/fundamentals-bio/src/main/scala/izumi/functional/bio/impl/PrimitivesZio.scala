package izumi.functional.bio.impl

import izumi.functional.bio.{Primitives2, Promise2, Ref2, Semaphore2}
import zio._
import zio.stm.TSemaphore

object PrimitivesZio extends PrimitivesZio

class PrimitivesZio extends Primitives2[IO] {
  override def mkRef[A](a: A): IO[Nothing, Ref2[IO, A]] = {
    Ref.make(a).map(Ref2.fromZIO)
  }
  override def mkPromise[E, A]: IO[Nothing, Promise2[IO, E, A]] = {
    Promise.make[E, A].map(Promise2.fromZIO)
  }
  override def mkSemaphore(permits: Long): IO[Nothing, Semaphore2[IO]] = {
    TSemaphore.make(permits).map(Semaphore2.fromZIO).commit
  }
}
