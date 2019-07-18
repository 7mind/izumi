package com.github.pshirshov.izumi.distage.testkit.services

import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.testkit.services.DISyntaxBIO.BIOBadBranch
import com.github.pshirshov.izumi.functional.bio.BIOError
import distage.{Tag, TagKK}

trait DISyntaxBIO[F[+_, +_]] { this: DISyntax[F[Throwable, ?]] =>

  implicit def tagBIO: TagKK[F]

  final def dio(function: ProviderMagnet[F[_, _]])(implicit dummy: DummyImplicit): Unit = {
    val fAsThrowable: ProviderMagnet[F[Throwable, _]] = function
      .zip(ProviderMagnet.identity[BIOError[F]])
      .map[F[Throwable, _]] {
        case (effect, bio) =>
          bio.leftMap(effect) {
            case t: Throwable => t
            case otherError: Any => BIOBadBranch(otherError)
          }
      }

    dio(fAsThrowable)
  }

  final def dio[T: Tag](function: T => F[_, _])(implicit dummy: DummyImplicit): Unit = {
    val providerMagnet: ProviderMagnet[F[_, _]] = {
      x: T =>
        function(x)
    }
    dio(providerMagnet)
  }

}

object DISyntaxBIO {
  final case class BIOBadBranch[A](error: A)
    extends RuntimeException(s"Test failed, unexpectedly got bad branch. Cause: $error")
}
