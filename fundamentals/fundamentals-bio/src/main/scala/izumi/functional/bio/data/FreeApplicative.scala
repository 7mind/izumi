package izumi.functional.bio.data

/** Scenario applicative is needed to create Pure scenario instance for [[Free.map]] implementation. */
trait FreeApplicative[S[_[+_, +_], _, _]] {
  def pure[F[+_, +_], A](a: => A): S[F, Nothing, A]
}

object FreeApplicative {
  def apply[S[_[+_, +_], _, _]: FreeApplicative]: FreeApplicative[S] = implicitly
}
