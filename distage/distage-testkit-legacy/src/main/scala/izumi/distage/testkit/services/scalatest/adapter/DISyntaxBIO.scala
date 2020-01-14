package izumi.distage.testkit.services.scalatest.adapter

import distage.Tag
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.testkit.services.DISyntaxBIOBase
import izumi.fundamentals.platform.language.CodePositionMaterializer

@deprecated("Use dstest", "2019/Jul/18")
trait DISyntaxBIO[F[+ _, + _]] extends DISyntaxBIOBase[F] {

  final def dio(function: ProviderMagnet[F[_, _]])(implicit pos: CodePositionMaterializer, dummyImplicit: DummyImplicit): Unit = {
    takeBIO(function, pos.get)
  }

  final def dio[T: Tag](function: T => F[_, _])(implicit pos: CodePositionMaterializer, dummyImplicit: DummyImplicit): Unit = {
    takeFunBIO(function, pos.get)
  }

}
