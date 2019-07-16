package com.github.pshirshov.izumi.distage.testkit.services

import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import distage.{Tag, TagK}


trait DISyntax[F[_]] {
  implicit def tagMonoIO: TagK[F]

  def dio(function: ProviderMagnet[F[_]])(implicit pos: CodePositionMaterializer): Unit

  final def dio[T: Tag](function: T => F[_])(implicit pos: CodePositionMaterializer): Unit = {
    val providerMagnet: ProviderMagnet[F[_]] = {
      x: T =>
        function(x)
    }
    dio(providerMagnet)(pos)
  }

  final def di[T: Tag](function: T => Any)(implicit pos: CodePositionMaterializer): Unit = {
    val providerMagnet: ProviderMagnet[Any] = {
      x: T =>
        function(x)
    }
    di(providerMagnet)(pos)
  }

  final def di(function: ProviderMagnet[Any])(implicit pos: CodePositionMaterializer): Unit = {
    val providerMagnet: ProviderMagnet[DIEffect[F]] = {
      eff: DIEffect[F] =>
        eff
    }

    val f: ProviderMagnet[F[Any]] = function.zip(providerMagnet).map {
      case (u, e) =>
        e.pure(u)
    }

    dio(f)(pos)
  }

}
