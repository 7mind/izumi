package com.github.pshirshov.izumi.distage.testkit.services.st.adapter

import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.testkit.services.DISyntaxBase
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import distage.Tag

@deprecated("Use dstest", "2019/Jul/18")
trait DISyntax[F[_]] extends DISyntax0[F, Unit]
trait DISyntax0[F[_], A] extends DISyntaxBase[F, A] {

  final def dio(function: ProviderMagnet[F[_]])(implicit pos: CodePositionMaterializer): A = {
    takeIO(function, pos.get)
  }

  final def dio[T: Tag](function: T => F[_])(implicit pos: CodePositionMaterializer): A = {
    takeFunIO(function, pos.get)
  }

  final def di[T: Tag](function: T => Any)(implicit pos: CodePositionMaterializer): A = {
    takeFunAny(function, pos.get)
  }

  final def di(function: ProviderMagnet[Any])(implicit pos: CodePositionMaterializer): A = {
    takeAny(function, pos.get)
  }

}
