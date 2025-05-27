package izumi.functional.bio.unsafe

import izumi.functional.bio.{Applicative2, IO2}

final class MaybeSuspend2[F[+_, +_]] {
  /** Will suspend the computation if `F` is lazy. Or won't if it's not. */
  def maybeSuspend[A](effect: => A)(implicit F: Applicative2[F]): F[Nothing, A] = {
    (F: @unchecked) match {
      case io2: IO2[F] => io2.sync(effect)
      case _ => F.map(F.unit)(_ => effect)
    }
  }
}
