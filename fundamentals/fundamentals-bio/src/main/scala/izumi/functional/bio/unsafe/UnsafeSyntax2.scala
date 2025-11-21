package izumi.functional.bio.unsafe

import izumi.functional.bio.Applicative2
import izumi.functional.bio.unsafe.UnsafeSyntax2.MaybeSuspend2Syntax

import scala.language.implicitConversions

trait UnsafeSyntax2 {
  implicit final def MaybeSuspend2[F[+_, +_]](F: Applicative2[F]): MaybeSuspend2Syntax[F] = new MaybeSuspend2Syntax[F](F)
}

object UnsafeSyntax2 {
  final class MaybeSuspend2Syntax[F[+_, +_]](private val F: Applicative2[F]) extends AnyVal {
    def maybeSuspend[A](effect: => A)(implicit F0: MaybeSuspend2[F]): F[Nothing, A] = {
      F0.maybeSuspend(effect)(using F)
    }
  }
}
