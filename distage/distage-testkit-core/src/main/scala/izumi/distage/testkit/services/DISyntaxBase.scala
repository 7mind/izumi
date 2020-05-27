package izumi.distage.testkit.services

import distage.{Tag, TagK}
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.providers.ProviderMagnet
import izumi.fundamentals.platform.language.SourceFilePosition

trait DISyntaxBase[F[_]] {
  implicit def tagMonoIO: TagK[F]

  protected def takeIO(function: ProviderMagnet[F[_]], pos: SourceFilePosition): Unit

  protected final def takeAny(function: ProviderMagnet[Any], pos: SourceFilePosition): Unit = {
    val f: ProviderMagnet[F[Any]] = function.flatAp {
      F: DIEffect[F] => (a: Any) =>
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
