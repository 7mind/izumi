package izumi.functional.bio.impl

import izumi.functional.bio.{Primitives2, Promise2, Ref2, Semaphore2}
import izumi.fundamentals.platform.language.Quirks.Discarder
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stm.TSemaphore
import zio.{Promise, Ref, ZIO}

object PrimitivesZio extends PrimitivesZio[Any]

open class PrimitivesZio[R] extends Primitives2[ZIO[R, +_, +_]] {

  override def mkRef[A](a: A): ZIO[R, Nothing, Ref2[ZIO[R, +_, +_], A]] = {
    implicit val trace: zio.Trace = Tracer.newTrace

    Ref.make(a).map(Ref2.fromZIO)
  }

  override def mkPromise[E, A]: ZIO[R, Nothing, Promise2[ZIO[R, +_, +_], E, A]] = {
    implicit val trace: zio.Trace = Tracer.newTrace

    Promise.make[E, A].map(Promise2.fromZIO)
  }

  override def mkSemaphore(permits: Long): ZIO[R, Nothing, Semaphore2[ZIO[R, +_, +_]]] = {
    implicit val trace: zio.Trace = Tracer.newTrace

    // `Semaphore2.fromZIO` returns `Semaphore2[zio.IO] = Semaphore2[ZIO[Any, _, _]]`.
    // Now that `Semaphore2[F[+_, +_]]` is invariant in F, we widen the environment parameter
    // explicitly. The cast is sound: a semaphore over `ZIO[Any, _, _]` works for any R because
    // ZIO[R, _, _] is contravariant in R (a value not needing R works in an environment that has R).
    TSemaphore.make(permits).map(s => Semaphore2.fromZIO(s).asInstanceOf[Semaphore2[ZIO[R, +_, +_]]]).commit
  }

  disableAutoTrace.discard()
}
