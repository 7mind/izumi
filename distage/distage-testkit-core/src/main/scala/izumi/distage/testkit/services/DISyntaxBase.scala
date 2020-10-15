package izumi.distage.testkit.services

import distage.{Tag, TagK}
import izumi.distage.model.effect.QuasiEffect
import izumi.distage.model.providers.Functoid
import izumi.fundamentals.platform.language.SourceFilePosition

trait DISyntaxBase[F[_]] {
  implicit def tagMonoIO: TagK[F]

  protected def takeIO(function: Functoid[F[_]], pos: SourceFilePosition): Unit

  protected final def takeAny(function: Functoid[Any], pos: SourceFilePosition): Unit = {
    val f: Functoid[F[Any]] = function.flatAp {
      F: QuasiEffect[F] => (a: Any) =>
        F.pure(a)
    }

    takeIO(f, pos)
  }

  protected final def takeFunIO[T: Tag](function: T => F[_], pos: SourceFilePosition): Unit = {
    takeIO(function, pos)
  }

  protected final def takeFunAny[T: Tag](function: T => Any, pos: SourceFilePosition): Unit = {
    takeAny(function, pos)
  }
}
