package izumi.functional.bio.impl

import izumi.functional.bio.{Mutex2, PrimitivesM2, RefM2}
import izumi.fundamentals.platform.language.Quirks.Discarder
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Ref, ZIO}

object PrimitivesMZio extends PrimitivesMZio[Any]

open class PrimitivesMZio[R] extends PrimitivesM2[ZIO[R, +_, +_]] {
  override def mkRefM[A](a: A): ZIO[R, Nothing, RefM2[ZIO[R, +_, +_], A]] = {
    implicit val trace: zio.Trace = Tracer.newTrace

    Ref.Synchronized.make(a).map(RefM2.fromZIO)
  }
  override def mkMutex: ZIO[R, Nothing, Mutex2[ZIO[R, +_, +_]]] = {
    Mutex2.createFromBIO[ZIO[R, +_, +_]]
  }

  disableAutoTrace.discard()
}
