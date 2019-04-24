package com.github.pshirshov.izumi.distage.testkit.services

import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import distage.Tag


abstract class DISyntax[F[_] : TagK] {

  protected def dio(function: ProviderMagnet[F[_]]): Unit

  protected final def dio[T: Tag](function: T => F[_]): Unit = {
    val providerMagnet: ProviderMagnet[F[_]] = {
      x: T =>
        function(x)
    }
    dio(providerMagnet)
  }

  protected final def di[T: Tag](function: T => Any): Unit = {
    val providerMagnet: ProviderMagnet[Any] = {
      x: T =>
        function(x)
    }
    di(providerMagnet)
  }

  protected final def di(function: ProviderMagnet[Any]): Unit = {
    val providerMagnet: ProviderMagnet[DIEffect[F]] = {
      eff: DIEffect[F] =>
        eff
    }

    val f: ProviderMagnet[F[Any]] = function.zip(providerMagnet).map {
      case (u, e) =>
        e.maybeSuspend(u)
    }

    dio(f)
  }

}
