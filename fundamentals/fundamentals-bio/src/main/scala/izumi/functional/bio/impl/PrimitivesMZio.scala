package izumi.functional.bio.impl

import izumi.functional.bio.{Mutex2, PrimitivesM2, RefM2}
import izumi.fundamentals.platform.language.Quirks.Discarder
import zio.internal.stacktracer.Tracer
import zio.{IO, Ref}
import zio.stacktracer.TracingImplicits.disableAutoTrace

object PrimitivesMZio extends PrimitivesMZio

open class PrimitivesMZio extends PrimitivesM2[IO] {
  override def mkRefM[A](a: A): IO[Nothing, RefM2[IO, A]] = {
    implicit val trace: zio.Trace = Tracer.newTrace

    Ref.Synchronized.make(a).map(RefM2.fromZIO)
  }
  override def mkMutex: IO[Nothing, Mutex2[IO]] = {
    Mutex2.createFromBIO
  }

  disableAutoTrace.discard()
}
