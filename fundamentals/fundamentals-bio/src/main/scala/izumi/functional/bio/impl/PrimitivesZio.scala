package izumi.functional.bio.impl

import izumi.functional.bio.{Primitives2, Promise2, Ref2, Semaphore2}
import izumi.fundamentals.platform.language.Quirks.Discarder
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stm.TSemaphore
import zio.{IO, Promise, Ref}

object PrimitivesZio extends PrimitivesZio

open class PrimitivesZio extends Primitives2[IO] {
  override def mkRef[A](a: A): IO[Nothing, Ref2[IO, A]] = {
    implicit val trace: zio.Trace = Tracer.newTrace

    Ref.make(a).map(Ref2.fromZIO)
  }
  override def mkPromise[E, A]: IO[Nothing, Promise2[IO, E, A]] = {
    implicit val trace: zio.Trace = Tracer.newTrace

    Promise.make[E, A].map(Promise2.fromZIO)
  }
  override def mkSemaphore(permits: Long): IO[Nothing, Semaphore2[IO]] = {
    implicit val trace: zio.Trace = Tracer.newTrace

    TSemaphore.make(permits).map(Semaphore2.fromZIO).commit
  }

  disableAutoTrace.discard()
}
