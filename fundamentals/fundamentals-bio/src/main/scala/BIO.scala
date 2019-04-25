import com.github.pshirshov.izumi.functional.bio.{BIO, BIOSyntax}

object BIO extends BIOSyntax {
  @inline def apply[F[+_, +_], A](effect: => A)(implicit BIO: BIO[F]): F[Throwable, A] = BIO.eff(effect)
  @inline def apply[R[+ _, + _] : BIO]: BIO[R] = implicitly

  object catz extends BIOCatsConversions
}
