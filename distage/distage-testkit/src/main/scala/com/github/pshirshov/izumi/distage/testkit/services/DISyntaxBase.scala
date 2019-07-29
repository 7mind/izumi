package com.github.pshirshov.izumi.distage.testkit.services

import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.fundamentals.platform.jvm.CodePosition
import distage.{Tag, TagK}

trait DISyntaxBase[F[_], A] {
  protected implicit def tagMonoIO: TagK[F]

  protected def takeIO(function: ProviderMagnet[F[_]], pos: CodePosition): A

  protected final def takeAny(function: ProviderMagnet[Any], pos: CodePosition): A = {
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

  protected final def takeFunIO[T: Tag](function: T => F[_], pos: CodePosition): A = {
    val providerMagnet: ProviderMagnet[F[_]] = {
      x: T =>
        function(x)
    }
    takeIO(providerMagnet, pos)
  }

  protected final def takeFunAny[T: Tag](function: T => Any, pos: CodePosition): A = {
    val providerMagnet: ProviderMagnet[Any] = {
      x: T =>
        function(x)
    }
    takeAny(providerMagnet, pos)
  }
}
