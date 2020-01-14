package izumi.distage.testkit.services

import izumi.distage.model.effect.DIEffect
import izumi.distage.model.providers.ProviderMagnet
import distage.{Tag, TagK}
import izumi.fundamentals.platform.language.CodePosition

trait DISyntaxBase[F[_]] {
  implicit def tagMonoIO: TagK[F]

  protected def takeIO(function: ProviderMagnet[F[_]], pos: CodePosition): Unit

  protected final def takeAny(function: ProviderMagnet[Any], pos: CodePosition): Unit = {
    val f: ProviderMagnet[F[Any]] = function.flatAp {
      F: DIEffect[F] =>
        (a: Any) =>
          F.pure(a)
    }

    takeIO(f, pos)
  }

  protected final def takeFunIO[T: Tag](function: T => F[_], pos: CodePosition): Unit = {
    takeIO(function, pos)
  }

  protected final def takeFunAny[T: Tag](function: T => Any, pos: CodePosition): Unit = {
    takeAny(function, pos)
  }
}
