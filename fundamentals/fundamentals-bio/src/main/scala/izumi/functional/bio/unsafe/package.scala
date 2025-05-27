package izumi.functional.bio

import scala.language.implicitConversions

package object unsafe {

  implicit final def MaybeSuspend2[F[+_, +_]](F: Applicative2[F]): MaybeSuspend2Syntax[F] = new MaybeSuspend2Syntax[F](F)
  implicit final def MaybeSuspend2[F[+_, +_]]: MaybeSuspend2[F] = new MaybeSuspend2[F]

  final class MaybeSuspend2Syntax[F[+_, +_]](private val F: Applicative2[F]) extends AnyVal {
    def maybeSuspend[A](effect: => A)(implicit F0: MaybeSuspend2[F]): F[Nothing, A] = {
      F0.maybeSuspend(effect)(using F)
    }
  }

}
