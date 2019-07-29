package com.github.pshirshov.izumi.distage.testkit.services

import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.testkit.services.DISyntaxBIOBase.BIOBadBranch
import com.github.pshirshov.izumi.functional.bio.BIOError
import com.github.pshirshov.izumi.fundamentals.platform.jvm.CodePosition
import distage.{Tag, TagKK}

trait DISyntaxBIOBase[F[+ _, + _], A] {

  implicit def tagBIO: TagKK[F]

  protected def takeAs1(fAsThrowable: ProviderMagnet[F[Throwable, _]], pos: CodePosition): A

  protected final def take2(function: ProviderMagnet[F[_, _]], pos: CodePosition): A = {
    val fAsThrowable: ProviderMagnet[F[Throwable, _]] = function
      .zip(ProviderMagnet.identity[BIOError[F]])
      .map[F[Throwable, _]] {
      case (effect, bio) =>
        bio.leftMap(effect) {
          case t: Throwable => t
          case otherError: Any => BIOBadBranch(otherError)
        }
    }

    takeAs1(fAsThrowable, pos)
  }

  protected final def take2[T: Tag](function: T => F[_, _], pos: CodePosition): A = {
    val providerMagnet: ProviderMagnet[F[_, _]] = {
      x: T =>
        function(x)
    }
    take2(providerMagnet, pos)
  }

}

object DISyntaxBIOBase {

  final case class BIOBadBranch[A](error: A)
    extends RuntimeException(s"Test failed, unexpectedly got bad branch. Cause: $error", null, false, false)

}
