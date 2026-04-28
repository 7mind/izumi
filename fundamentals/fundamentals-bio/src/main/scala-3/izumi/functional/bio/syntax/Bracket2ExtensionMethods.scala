package izumi.functional.bio.syntax

import izumi.functional.bio.{Bracket2, Exit}

import scala.annotation.targetName

trait Bracket2ExtensionMethods {
  extension [F[+_, +_], E, A](r: F[E, A])(using F: Bracket2[F]) {
    @targetName("bracketExt")
    def bracket[E1 >: E, B](release: A => F[Nothing, Unit])(use: A => F[E1, B]): F[E1, B] =
      F.bracket(r.asInstanceOf[F[E1, A]])(release)(use)

    @targetName("bracketCaseExt")
    def bracketCase[E1 >: E, B](release: (A, Exit[E1, B]) => F[Nothing, Unit])(use: A => F[E1, B]): F[E1, B] =
      F.bracketCase(r.asInstanceOf[F[E1, A]])(release)(use)

    @targetName("guaranteeCaseExt")
    def guaranteeCase(cleanup: Exit[E, A] => F[Nothing, Unit]): F[E, A] =
      F.guaranteeCase(r, cleanup)

    @targetName("bracketOnFailureExt")
    def bracketOnFailure[E1 >: E, B](cleanupOnFailure: (A, Exit.Failure[E1]) => F[Nothing, Unit])(use: A => F[E1, B]): F[E1, B] =
      F.bracketOnFailure(r.asInstanceOf[F[E1, A]])(cleanupOnFailure)(use)

    @targetName("guaranteeOnFailureExt")
    def guaranteeOnFailure(cleanupOnFailure: Exit.Failure[E] => F[Nothing, Unit]): F[E, A] =
      F.guaranteeOnFailure(r, cleanupOnFailure)

    @targetName("guaranteeOnInterruptExt")
    def guaranteeOnInterrupt(cleanupOnInterruption: Exit.Interruption => F[Nothing, Unit]): F[E, A] =
      F.guaranteeOnInterrupt(r, cleanupOnInterruption)

    @targetName("guaranteeExceptOnInterruptExt")
    def guaranteeExceptOnInterrupt(cleanupOnNonInterruption: Exit.Uninterrupted[E, A] => F[Nothing, Unit]): F[E, A] =
      F.guaranteeExceptOnInterrupt(r, cleanupOnNonInterruption)
  }
}
