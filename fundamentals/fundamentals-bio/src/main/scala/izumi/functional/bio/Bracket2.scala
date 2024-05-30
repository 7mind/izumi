package izumi.functional.bio

trait Bracket2[F[+_, +_]] extends Error2[F] {
  def bracketCase[E, A, B](acquire: F[E, A])(release: (A, Exit[E, B]) => F[Nothing, Unit])(use: A => F[E, B]): F[E, B]

  def bracket[E, A, B](acquire: F[E, A])(release: A => F[Nothing, Unit])(use: A => F[E, B]): F[E, B] = {
    bracketCase(acquire)((a, _: Exit[E, B]) => release(a))(use)
  }

  def guaranteeCase[E, A](f: F[E, A], cleanup: Exit[E, A] => F[Nothing, Unit]): F[E, A] = {
    bracketCase(unit: F[E, Unit])((_, e: Exit[E, A]) => cleanup(e))(_ => f)
  }

  /**
    * Run release action only on a failure – _any failure_, INCLUDING interruption.
    * Do not run release action if `use` finished successfully.
    */
  final def bracketOnFailure[E, A, B](acquire: F[E, A])(cleanupOnFailure: (A, Exit.Failure[E]) => F[Nothing, Unit])(use: A => F[E, B]): F[E, B] = {
    bracketCase[E, A, B](acquire) {
      case (a, e: Exit.Failure[E]) => cleanupOnFailure(a, e)
      case _ => unit
    }(use)
  }

  /**
    * Run cleanup only on a failure – _any failure_, INCLUDING interruption.
    * Do not run cleanup if `use` finished successfully.
    */
  final def guaranteeOnFailure[E, A](f: F[E, A], cleanupOnFailure: Exit.Failure[E] => F[Nothing, Unit]): F[E, A] = {
    guaranteeCase[E, A](f, { case e: Exit.Failure[E] => cleanupOnFailure(e); case _ => unit })
  }

  /**
    * Run cleanup only on interruption.
    * Do not run cleanup if `use` finished successfully.
    */
  final def guaranteeOnInterrupt[E, A](f: F[E, A], cleanupOnInterruption: Exit.Interruption => F[Nothing, Unit]): F[E, A] = {
    guaranteeCase[E, A](f, { case e: Exit.Interruption => cleanupOnInterruption(e); case _ => unit })
  }

  /** Run cleanup on both _success_ and _failure_, if the failure IS NOT an interruption. */
  final def guaranteeExceptOnInterrupt[E, A](
    f: F[E, A],
    cleanupOnNonInterruption: Exit.Uninterrupted[E, A] => F[Nothing, Unit],
  ): F[E, A] = {
    guaranteeCase[E, A](
      f,
      {
        case e: Exit.Termination => cleanupOnNonInterruption(e)
        case e: Exit.Error[E] => cleanupOnNonInterruption(e)
        case e: Exit.Success[A] => cleanupOnNonInterruption(e)
        case _: Exit.Interruption => unit
      },
    )
  }

  // defaults
  override def guarantee[E, A](f: F[E, A], cleanup: F[Nothing, Unit]): F[E, A] = {
    bracket(unit: F[E, Unit])(_ => cleanup)(_ => f)
  }
}
