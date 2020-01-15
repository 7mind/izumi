package izumi.distage.testkit.services

import distage.{Tag, TagKK}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.testkit.services.DISyntaxBIOBase.BIOBadBranch
import izumi.functional.bio.BIOError
import izumi.fundamentals.platform.language.CodePosition

trait DISyntaxBIOBase[F[+_, +_]] extends DISyntaxBase[F[Throwable, ?]] {
  implicit def tagBIO: TagKK[F]

  protected final def takeBIO(function: ProviderMagnet[F[_, _]], pos: CodePosition): Unit = {
    val fAsThrowable: ProviderMagnet[F[Throwable, _]] = function
      .map2(ProviderMagnet.identity[BIOError[F]]) {
        (effect, F) =>
          F.leftMap(effect) {
            case t: Throwable => t
            case otherError: Any => BIOBadBranch(otherError)
          }
      }

    takeIO(fAsThrowable, pos)
  }

  protected final def takeFunBIO[T: Tag](function: T => F[_, _], pos: CodePosition): Unit = {
    takeBIO(function, pos)
  }

}

object DISyntaxBIOBase {
  final case class BIOBadBranch[A](error: A)
    extends RuntimeException(s"Test failed, unexpectedly got bad branch. Cause: $error", null, true, false)
}
