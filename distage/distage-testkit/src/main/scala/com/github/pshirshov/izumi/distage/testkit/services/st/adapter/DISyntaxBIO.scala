package com.github.pshirshov.izumi.distage.testkit.services.st.adapter

import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.testkit.services.DISyntaxBIOBase
import com.github.pshirshov.izumi.fundamentals.platform.jvm.CodePosition
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import distage.Tag

@deprecated("Use dstest", "2019/Jul/18")
trait DISyntaxBIO[F[+ _, + _]] extends DISyntaxBIO0[F, Unit] { this: DISyntax[F[Throwable, ?]] => }
trait DISyntaxBIO0[F[+ _, + _], A] extends DISyntaxBIOBase[F, A] {
  this: DISyntax0[F[Throwable, ?], A] =>

  override protected def takeAs1(fAsThrowable: ProviderMagnet[F[Throwable, _]], pos: CodePosition): A = dio(fAsThrowable)

  final def dio(function: ProviderMagnet[F[_, _]])(implicit pos: CodePositionMaterializer, dummyImplicit: DummyImplicit): A = {
    take2(function, pos.get)
  }

  final def dio[T: Tag](function: T => F[_, _])(implicit pos: CodePositionMaterializer, dummyImplicit: DummyImplicit): A = {
    take2(function, pos.get)
  }

}
