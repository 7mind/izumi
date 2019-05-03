package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.testkit.DistageBioSpecBIOSyntax.BIOBadBranch
import com.github.pshirshov.izumi.functional.bio.BIOError
import distage.{Tag, TagKK}

trait DistageBioSpecBIOSyntax[F[+_, +_]] { this: DistageTestSupport[F[Throwable, ?]] =>

  implicit def tagKK: TagKK[F]

  protected final def dio(function: ProviderMagnet[F[_, _]])(implicit dummy: DummyImplicit): Unit = {
    val fAsThrowable: ProviderMagnet[F[Throwable, _]] = function
      .zip(ProviderMagnet.identity[BIOError[F]])
      .map[F[Throwable, _]] {
        case (effect, bio) =>
          bio.catchAll(effect) {
            case t: Throwable => bio.fail(t)
            case otherError: Any => bio.fail(BIOBadBranch(otherError))
          }
      }

    dio(fAsThrowable)
  }

  protected final def dio[T: Tag](function: T => F[_, _])(implicit dummy: DummyImplicit): Unit = {
    val providerMagnet: ProviderMagnet[F[_, _]] = {
      x: T =>
        function(x)
    }
    dio(providerMagnet)
  }

}

object DistageBioSpecBIOSyntax {
  final case class BIOBadBranch[A](error: A)
    extends RuntimeException(s"Test failed, unexpectedly got bad branch. Cause: $error")
}
