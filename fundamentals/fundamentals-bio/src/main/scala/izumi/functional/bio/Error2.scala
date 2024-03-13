package izumi.functional.bio

import izumi.fundamentals.platform.language.SourceFilePositionMaterializer

import scala.annotation.nowarn

trait Error2[F[+_, +_]] extends ApplicativeError2[F] with Monad2[F] with ErrorAccumulatingOps2[F] {

  def catchAll[E, A, E2](r: F[E, A])(f: E => F[E2, A]): F[E2, A]

  def catchSome[E, A, E1 >: E](r: F[E, A])(f: PartialFunction[E, F[E1, A]]): F[E1, A] = {
    catchAll(r)(e => f.applyOrElse(e, (_: E) => fail(e)))
  }

  def redeem[E, A, E2, B](r: F[E, A])(err: E => F[E2, B], succ: A => F[E2, B]): F[E2, B] = {
    flatMap(attempt(r))(_.fold(err, succ))
  }
  def redeemPure[E, A, B](r: F[E, A])(err: E => B, succ: A => B): F[Nothing, B] = catchAll(map(r)(succ))(e => pure(err(e)))
  def attempt[E, A](r: F[E, A]): F[Nothing, Either[E, A]] = redeemPure(r)(Left(_), Right(_))

  def tapError[E, A, E1 >: E](r: F[E, A])(f: E => F[E1, Unit]): F[E1, A] = {
    catchAll(r)(e => *>(f(e), fail(e)))
  }

  def flip[E, A](r: F[E, A]): F[A, E] = {
    redeem(r)(pure, fail(_))
  }
  def leftFlatMap[E, A, E2](r: F[E, A])(f: E => F[Nothing, E2]): F[E2, A] = {
    redeem(r)(e => flatMap(f(e))(fail(_)), pure)
  }
  def tapBoth[E, A, E1 >: E](r: F[E, A])(err: E => F[E1, Unit], succ: A => F[E1, Unit]): F[E1, A] = {
    tap(tapError[E, A, E1](r)(err), succ)
  }

  /** Extracts the optional value or fails with the `errorOnNone` error */
  def fromOption[E, A](errorOnNone: => E, r: F[E, Option[A]]): F[E, A] = {
    flatMap(r) {
      case Some(value) => pure(value)
      case None => fail(errorOnNone)
    }
  }

  /** Retries this effect while its error satisfies the specified predicate. */
  def retryWhile[E, A](r: F[E, A])(f: E => Boolean): F[E, A] = {
    retryWhileF(r)(e => pure(f(e)))
  }
  /** Retries this effect while its error satisfies the specified effectful predicate. */
  def retryWhileF[E, A](r: F[E, A])(f: E => F[Nothing, Boolean]): F[E, A] = {
    catchAll(r: F[E, A])(e => flatMap(f(e))(if (_) retryWhileF(r)(f) else fail(e)))
  }

  /** Retries this effect until its error satisfies the specified predicate. */
  def retryUntil[E, A](r: F[E, A])(f: E => Boolean): F[E, A] = {
    retryUntilF(r)(e => pure(f(e)))
  }
  /** Retries this effect until its error satisfies the specified effectful predicate. */
  def retryUntilF[E, A](r: F[E, A])(f: E => F[Nothing, Boolean]): F[E, A] = {
    catchAll(r: F[E, A])(e => flatMap(f(e))(if (_) fail(e) else retryUntilF(r)(f)))
  }

  @nowarn("msg=Unused import")
  def partition[E, A](l: Iterable[F[E, A]]): F[Nothing, (List[E], List[A])] = {
    import scala.collection.compat.*
    map(traverse(l)(attempt[E, A]))(_.partitionMap(identity))
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
  @inline final def withFilter[E, A](r: F[E, A])(predicate: A => Boolean)(implicit filter: WithFilter[E], pos: SourceFilePositionMaterializer): F[E, A] = {
    flatMap(r)(a => if (predicate(a)) pure(a) else fail(filter.error(a, pos.get)))
  }

  // defaults
  override def bimap[E, A, E2, B](r: F[E, A])(f: E => E2, g: A => B): F[E2, B] = catchAll(map(r)(g))(e => fail(f(e)))
  override def leftMap2[E, A, E2, E3](firstOp: F[E, A], secondOp: => F[E2, A])(f: (E, E2) => E3): F[E3, A] =
    catchAll(firstOp)(e => leftMap(secondOp)(f(e, _)))
  override def orElse[E, A, E2](r: F[E, A], f: => F[E2, A]): F[E2, A] = catchAll(r)(_ => f)
}
