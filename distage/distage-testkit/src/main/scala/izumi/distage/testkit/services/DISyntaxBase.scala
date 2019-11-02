package izumi.distage.testkit.services

import izumi.distage.model.monadic.DIEffect
import izumi.distage.model.providers.ProviderMagnet
import distage.{Tag, TagK}
import izumi.fundamentals.platform.language.CodePosition

trait DISyntaxBase[F[_]] {
  implicit def tagMonoIO: TagK[F]

  protected def takeIO(function: ProviderMagnet[F[_]], pos: CodePosition): Unit

  protected final def takeAny(function: ProviderMagnet[Any], pos: CodePosition): Unit = {
    val providerMagnet: ProviderMagnet[DIEffect[F]] = {
      eff: DIEffect[F] =>
        eff
    }

    val f: ProviderMagnet[F[Any]] = function.zip(providerMagnet).map {
      case (u, e) =>
        e.pure(u)
    }

    takeIO(f, pos)
  }

  protected final def takeFunIO[T: Tag](function: T => F[_], pos: CodePosition): Unit = {
    val providerMagnet: ProviderMagnet[F[_]] = {
      x: T =>
        function(x)
    }
    takeIO(providerMagnet, pos)
  }

  protected final def takeFunAny[T: Tag](function: T => Any, pos: CodePosition): Unit = {
    val providerMagnet: ProviderMagnet[Any] = {
      x: T =>
        function(x)
    }
    takeAny(providerMagnet, pos)
  }
}
