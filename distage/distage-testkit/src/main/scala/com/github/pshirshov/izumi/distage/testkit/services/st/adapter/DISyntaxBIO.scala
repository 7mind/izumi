package com.github.pshirshov.izumi.distage.testkit.services.st.adapter

import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.testkit.services.DISyntaxBIOBase
import com.github.pshirshov.izumi.fundamentals.platform.jvm.CodePosition
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import distage.Tag

@deprecated("Use dstest", "2019/Jul/18")
trait DISyntaxBIO[F[+ _, + _]] extends DISyntaxBIOBase[F] {
  this: DISyntax[F[Throwable, ?]] =>

  override protected def takeAs1(fAsThrowable: ProviderMagnet[F[Throwable, _]], pos: CodePosition): Unit = dio(fAsThrowable)

  final def dio(function: ProviderMagnet[F[_, _]])(implicit pos: CodePositionMaterializer, dummyImplicit: DummyImplicit): Unit = {
    take2(function, pos.get)
  }

  final def dio[T: Tag](function: T => F[_, _])(implicit pos: CodePositionMaterializer, dummyImplicit: DummyImplicit): Unit = {
    take2(function, pos.get)
  }

}
