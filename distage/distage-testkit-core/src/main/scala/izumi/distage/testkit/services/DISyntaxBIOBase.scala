package izumi.distage.testkit.services

import distage.{Tag, TagKK}
import izumi.distage.model.providers.Functoid
import izumi.functional.bio.{ApplicativeError2, TypedError}
import izumi.fundamentals.platform.language.SourceFilePosition

trait DISyntaxBIOBase[F[+_, +_]] extends DISyntaxBase[F[Throwable, ?]] {
  implicit def tagBIO: TagKK[F]

  protected final def takeBIO(function: Functoid[F[_, _]], pos: SourceFilePosition): Unit = {
    val fAsThrowable: Functoid[F[Throwable, _]] = function
      .map2(Functoid.identity[ApplicativeError2[F]]) {
        (effect, F) =>
          F.leftMap(effect) {
            case t: Throwable => t
            case otherError => TypedError("Test failed, unexpectedly got bad branch. ", otherError)
          }
      }

    takeIO(fAsThrowable, pos)
  }

  protected final def takeFunBIO[T: Tag](function: T => F[_, _], pos: SourceFilePosition): Unit = {
    takeBIO(function, pos)
  }

}
