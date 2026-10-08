package izumi.functional.bio.impl

import izumi.functional.bio.{BlockingIO2, Exit, IO2}

private[impl] trait MiniBIOInstance[F[+_, +_]] extends IO2[F] with BlockingIO2[F] {
  protected def halt[E](failure: Exit.FailureUninterrupted[E]): F[E, Nothing]
  protected def redeemExit[E, A, E2, B](r: F[E, A])(err: Exit.FailureUninterrupted[E] => F[E2, B], succ: A => F[E2, B]): F[E2, B]

  override def sync[A](effect: => A): F[Nothing, A] = fromSandboxExit(Exit.Success(effect))
  override def syncThrowable[A](effect: => A): F[Throwable, A] = fromSandboxExit {
    try Exit.Success(effect)
    catch { case error: Throwable => Exit.Error.forThrowable(error) }
  }

  override def redeem[E, A, E2, B](r: F[E, A])(err: E => F[E2, B], succ: A => F[E2, B]): F[E2, B] = {
    redeemExit(r)({
      case termination: Exit.Termination => halt(termination)
      case Exit.Error(error, _) => err(error)
    }, succ)
  }
  override def catchAll[E, A, E2](r: F[E, A])(f: E => F[E2, A]): F[E2, A] = redeem(r)(f, pure)
  override def sandbox[E, A](r: F[E, A]): F[Exit.FailureUninterrupted[E], A] = redeemExit(r)(e => fail(e), pure)

  override def traverse[E, A, B](l: Iterable[A])(f: A => F[E, B]): F[E, List[B]] = {
    val reversed = l.foldLeft(pure(Nil): F[E, List[B]]) {
      (acc, a) => flatMap(acc)(list => map(f(a))(_ :: list))
    }
    map(reversed)(_.reverse)
  }

  override def shiftBlocking[E, A](f: F[E, A]): F[E, A] = f
  override def syncInterruptibleBlocking[A](f: => A): F[Throwable, A] = syncBlocking(f)
  override def syncBlocking[A](f: => A): F[Throwable, A] = syncThrowable(scala.concurrent.blocking(f))
}
