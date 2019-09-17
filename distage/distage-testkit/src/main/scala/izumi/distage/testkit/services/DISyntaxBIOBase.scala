package izumi.distage.testkit.services

import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.testkit.services.DISyntaxBIOBase.BIOBadBranch
import izumi.functional.bio.BIOError
import izumi.fundamentals.platform.jvm.CodePosition
import distage.{Tag, TagKK}

trait DISyntaxBIOBase[F[+ _, + _]] {

  implicit def tagBIO: TagKK[F]

  protected def takeAs1(fAsThrowable: ProviderMagnet[F[Throwable, _]], pos: CodePosition): Unit

  protected final def take2(function: ProviderMagnet[F[_, _]], pos: CodePosition): Unit = {
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

  protected final def take2[T: Tag](function: T => F[_, _], pos: CodePosition): Unit = {
    val providerMagnet: ProviderMagnet[F[_, _]] = {
      x: T =>
        function(x)
    }
    take2(providerMagnet, pos)
  }

}

object DISyntaxBIOBase {

  final case class BIOBadBranch[A](error: A)
    extends RuntimeException(s"Test failed, unexpectedly got bad branch. Cause: $error")
}
