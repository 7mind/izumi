package izumi.functional.bio.impl

import izumi.functional.bio.{FiberRef2, PrimitivesLocal2}
import izumi.fundamentals.platform.language.Quirks.Discarder
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{FiberRef, Scope, ZEnvironment, ZIO}

object PrimitivesLocalZio extends PrimitivesLocalZio[Any]

open class PrimitivesLocalZio[R] extends PrimitivesLocal2[ZIO[R, +_, +_]] {

  override def mkFiberRef[A](a: A): ZIO[R, Nothing, FiberRef2[ZIO[R, +_, +_], A]] = {
    implicit val trace: zio.Trace = Tracer.newTrace

    FiberRef
      .make(a)
      .provideEnvironment(ZEnvironment[Scope](Scope.global))
      .map(FiberRef2.fromZIO[R, A])
  }

  disableAutoTrace.discard()
}
