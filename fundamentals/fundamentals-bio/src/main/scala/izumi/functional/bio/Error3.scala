package izumi.functional.bio

import izumi.fundamentals.platform.language.SourceFilePositionMaterializer

trait Error3[F[-_, +_, +_]] extends ApplicativeError3[F] with Monad3[F] {

  def catchAll[R, E, A, E2](r: F[R, E, A])(f: E => F[R, E2, A]): F[R, E2, A]
  def catchSome[R, E, A, E1 >: E](r: F[R, E, A])(f: PartialFunction[E, F[R, E1, A]]): F[R, E1, A]

  def redeem[R, E, A, E2, B](r: F[R, E, A])(err: E => F[R, E2, B], succ: A => F[R, E2, B]): F[R, E2, B] = {
    flatMap(attempt(r))(_.fold(err, succ))
  }
  def redeemPure[R, E, A, B](r: F[R, E, A])(err: E => B, succ: A => B): F[R, Nothing, B] = catchAll(map(r)(succ))(e => pure(err(e)))
  def attempt[R, E, A](r: F[R, E, A]): F[R, Nothing, Either[E, A]] = redeemPure(r)(Left(_), Right(_))

  def tapError[R, E, A, E1 >: E](r: F[R, E, A])(f: E => F[R, E1, Unit]): F[R, E1, A] = {
    catchAll(r)(e => *>(f(e), fail(e)))
  }

  def flip[R, E, A](r: F[R, E, A]): F[R, A, E] = {
    redeem(r)(pure, fail(_))
  }
  def leftFlatMap[R, E, A, E2](r: F[R, E, A])(f: E => F[R, Nothing, E2]): F[R, E2, A] = {
    redeem(r)(e => flatMap(f(e))(fail(_)), pure)
  }
  def tapBoth[R, E, A, E1 >: E](r: F[R, E, A])(err: E => F[R, E1, Unit], succ: A => F[R, E1, Unit]): F[R, E1, A] = {
    tap(tapError[R, E, A, E1](r)(err), succ)
  }

  /**
    * Retries this effect until its error satisfies the specified predicate.
    */
  def retryUntil[R, E, A](r: F[R, E, A])(f: E => Boolean): F[R, E, A] =
    retryUntilF(r)(e => pure(f(e)))

  /**
    * Retries this effect until its error satisfies the specified effectful predicate.
    */
  def retryUntilF[R, E, A](r: F[R, E, A])(f: E => F[R, Nothing, Boolean]): F[R, E, A] =
    catchAll(r) {
      e =>
        flatMap(f(e)) {
          b =>
            if (b) {
              fail(e)
            } else {
              retryUntilF(r)(f)
            }
        }
    }

  /**
    * Retries this effect while its error satisfies the specified predicate.
    */
  def retryWhile[R, E, A](r: F[R, E, A])(f: E => Boolean): F[R, E, A] =
    retryWhileF(r)(e => pure(f(e)))

  /**
    * Retries this effect while its error satisfies the specified effectful predicate.
    */
  def retryWhileF[R, E, A](r: F[R, E, A])(f: E => F[R, Nothing, Boolean]): F[R, E, A] =
    retryUntilF(r)(e => map(f(e))(!_))

  /**
    * Extracts the optional value, or returns the given 'default'.
    */
  def fromOptionOr[R, E, A](r: F[R, E, Option[A]])(default: => A): F[R, E, A] =
    map(r)(_.getOrElse(default))

  /**
    * Extracts the optional value, or executes the effect 'default'.
    */
  def fromOptionF[R, E, A](r: F[R, E, Option[A]])(default: F[R, E, A]): F[R, E, A] =
    flatMap(r) {
      case Some(value) => pure(value)
      case None => default
    }
  /** for-comprehensions sugar:
    *
    * {{{
    *   for {
    *     (1, 2) <- F.pure((2, 1))
    *   } yield ()
    * }}}
    *
    * Use [[widenError]] to for pattern matching with non-Throwable errors:
    *
    * {{{
    *   val f = for {
    *     (1, 2) <- F.pure((2, 1)).widenError[Option[Unit]]
    *   } yield ()
    *   // f: F[Option[Unit], Unit] = F.fail(Some(())
    * }}}
    */
  @inline final def withFilter[R, E, A](r: F[R, E, A])(predicate: A => Boolean)(implicit filter: WithFilter[E], pos: SourceFilePositionMaterializer): F[R, E, A] = {
    flatMap(r)(a => if (predicate(a)) pure(a) else fail(filter.error(a, pos.get)))
  }

  // defaults
  override def bimap[R, E, A, E2, B](r: F[R, E, A])(f: E => E2, g: A => B): F[R, E2, B] = catchAll(map(r)(g))(e => fail(f(e)))
  override def leftMap2[R, E, A, E2, E3](firstOp: F[R, E, A], secondOp: => F[R, E2, A])(f: (E, E2) => E3): F[R, E3, A] =
    catchAll(firstOp)(e => leftMap(secondOp)(f(e, _)))
  override def orElse[R, E, A, E2](r: F[R, E, A], f: => F[R, E2, A]): F[R, E2, A] = catchAll(r)(_ => f)
}
