package izumi.functional.bio.impl

import cats.effect.IOLocal
import izumi.functional.bio.{FiberRef2, Panic2, PrimitivesLocal2}
import izumi.functional.bio.data.~>

open class PrimitivesLocalFromCatsIO[F[+_, +_]: Panic2](fromIO: cats.effect.IO ~> F[Throwable, _]) extends PrimitivesLocal2[F] {
  override def mkFiberRef[A](a: A): F[Nothing, FiberRef2[F, A]] = {
    fromIO(IOLocal.apply[A](a)).orTerminate
      .map(FiberRef2.fromCatsIOLocal[F, A](fromIO))
  }
}
