package izumi.distage.testkit.services.scalatest.adapter

import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.testkit.services.DISyntaxBIOBase
import distage.Tag
import izumi.fundamentals.platform.language.{CodePosition, CodePositionMaterializer}

@deprecated("Use dstest", "2019/Jul/18")
trait DISyntaxBIO[F[+ _, + _]] extends DISyntaxBIOBase[F] {
  this: DISyntax[F[Throwable, ?]] =>

  override protected def takeIO(fAsThrowable: ProviderMagnet[F[Throwable, _]], pos: CodePosition): Unit = dio(fAsThrowable)

  final def dio(function: ProviderMagnet[F[_, _]])(implicit pos: CodePositionMaterializer, dummyImplicit: DummyImplicit): Unit = {
    takeBIO(function, pos.get)
  }

  final def dio[T: Tag](function: T => F[_, _])(implicit pos: CodePositionMaterializer, dummyImplicit: DummyImplicit): Unit = {
    takeFunBIO(function, pos.get)
  }

}
