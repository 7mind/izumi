package izumi.functional.bio.impl

import cats.effect.IOLocal
import izumi.functional.bio.{FiberRef2, Panic2, PrimitivesLocal2}
import izumi.functional.bio.data.~>

@deprecated("Use izumi.functional.bio.impl.CatsToBIO.asyncToBIO for the full CE→BIO conversion. PrimitivesLocalFromCatsIO is a partial derivation kept for binary-compat; it will be removed in M5 when Quasi* is deleted.", "1.3.0")
open class PrimitivesLocalFromCatsIO[F[+_, +_]: Panic2](fromIO: cats.effect.IO ~> F[Throwable, _]) extends PrimitivesLocal2[F] {
  override def mkFiberRef[A](a: A): F[Nothing, FiberRef2[F, A]] = {
    fromIO(IOLocal.apply[A](a)).orTerminate
      .map(FiberRef2.fromCatsIOLocal[F, A](fromIO))
  }
}
