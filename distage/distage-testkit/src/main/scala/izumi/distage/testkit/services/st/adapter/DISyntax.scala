package izumi.distage.testkit.services.st.adapter

import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.testkit.services.DISyntaxBase
import izumi.fundamentals.reflection.CodePositionMaterializer
import distage.Tag

@deprecated("Use dstest", "2019/Jul/18")
trait DISyntax[F[_]] extends DISyntaxBase[F] {

  final def dio(function: ProviderMagnet[F[_]])(implicit pos: CodePositionMaterializer): Unit = {
    takeIO(function, pos.get)
  }

  final def dio[T: Tag](function: T => F[_])(implicit pos: CodePositionMaterializer): Unit = {
    takeFunIO(function, pos.get)
  }

  final def di[T: Tag](function: T => Any)(implicit pos: CodePositionMaterializer): Unit = {
    takeFunAny(function, pos.get)
  }

  final def di(function: ProviderMagnet[Any])(implicit pos: CodePositionMaterializer): Unit = {
    takeAny(function, pos.get)
  }

}
