package izumi.functional.bio.instances

trait BIOMonadError3[F[-_, +_, +_]] extends BIOError3[F] with BIOMonad3[F] {
  def redeem[R, E, A, E2, B](r: F[R, E, A])(err: E => F[R, E2, B], succ: A => F[R, E2, B]): F[R, E2, B] = {
    flatMap(attempt(r))(_.fold(err, succ))
  }

  def flip[R, E, A](r: F[R, E, A]): F[R, A, E] = {
    redeem(r)(pure, fail(_))
  }
  def leftFlatMap[R, E, A, E2](r: F[R, E, A])(f: E => F[R, Nothing, E2]): F[R, E2, A] = {
    redeem(r)(e => flatMap(f(e))(fail(_)), pure)
  }
  def tapBoth[R, E, A, E2 >: E](r: F[R, E, A])(err: E => F[R, E2, Unit], succ: A => F[R, E2, Unit]): F[R, E2, A] = {
    tap(tapError[R, E, A, E2](r)(err))(succ)
  }
  /** for-comprehensions sugar:
    *
    * {{{
    *   for {
    *    (1, 2) <- F.pure((2, 1))
    *   } yield ()
    * }}}
    */
  def withFilter[R, E, A](r: F[R, E, A])(predicate: A => Boolean)(implicit ev: NoSuchElementException <:< E): F[R, E, A] = {
    flatMap(r)(a => if (predicate(a)) pure(a) else fail(new NoSuchElementException("The value doesn't satisfy the predicate")))
  }
}
