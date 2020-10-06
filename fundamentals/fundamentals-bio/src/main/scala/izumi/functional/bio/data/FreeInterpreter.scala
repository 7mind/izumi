package izumi.functional.bio.data

/**
  * Should executed scenario steps in a single context and save all scenario metrics.
  */
trait FreeInterpreter[F[+_, +_], S[_[+_, +_], _, _], Scope] {
  /** Single step execution. */
  def interpret[E, A](scope: Scope, s: S[F, E, A]): F[Nothing, FreeInterpreter.Result[F, S, Scope]]
}

object FreeInterpreter {
  sealed trait Result[F[+_, +_], S[_[+_, +_], _, _], Scope] {
    def scope: Scope
  }
  object Result {
    final case class Success[F[+_, +_], S[_[+_, +_], _, _], Scope, A](scope: Scope, value: A) extends Result[F, S, Scope]
    final case class Error[F[+_, +_], S[_[+_, +_], _, _], Scope, E](scope: Scope, err: E, trace: String) extends Result[F, S, Scope]
    final case class Termination[F[+_, +_], S[_[+_, +_], _, _], Scope](scope: Scope, err: Throwable, all: List[Throwable], trace: String) extends Result[F, S, Scope]
  }
}
