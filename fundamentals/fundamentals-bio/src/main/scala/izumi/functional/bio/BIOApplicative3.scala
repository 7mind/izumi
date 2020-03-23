package izumi.functional.bio

trait BIOApplicative3[F[-_, +_, +_]] extends BIOFunctor3[F] {
  def pure[A](a: A): F[Any, Nothing, A]

  /** execute two operations in order, map their results */
  def map2[R, E, A, B, C](firstOp: F[R, E, A], secondOp: => F[R, E, B])(f: (A, B) => C): F[R, E, C]

  /** execute two operations in order, return result of second operation */
  def *>[R, E, A, B](firstOp: F[R, E, A], secondOp: => F[R, E, B]): F[R, E, B]

  /** execute two operations in order, same as `*>`, but return result of first operation */
  def <*[R, E, A, B](firstOp: F[R, E, A], secondOp: => F[R, E, B]): F[R, E, A]

  def traverse[R, E, A, B](l: Iterable[A])(f: A => F[R, E, B]): F[R, E, List[B]]

  @inline final def forever[R, E, A](r: F[R, E, A]): F[R, E, Nothing] = *>(r, forever(r))
  def traverse_[R, E, A](l: Iterable[A])(f: A => F[R, E, Unit]): F[R, E, Unit] = void(traverse(l)(f))
  def sequence[R, E, A, B](l: Iterable[F[R, E, A]]): F[R, E, List[A]] = traverse(l)(identity)
  def sequence_[R, E](l: Iterable[F[R, E, Unit]]): F[R, E, Unit] = void(traverse(l)(identity))

  final val unit: F[Any, Nothing, Unit] = pure(())
  @inline final def traverse[R, E, A, B](o: Option[A])(f: A => F[R, E, B]): F[R, E, Option[B]] = o match {
    case Some(a) => map(f(a))(Some(_))
    case None => pure(None)
  }
  @inline final def when[R, E](cond: Boolean)(ifTrue: F[R, E, Unit]): F[R, E, Unit] = if (cond) ifTrue else unit
  @inline final def unless[R, E](cond: Boolean)(ifFalse: F[R, E, Unit]): F[R, E, Unit] = if (cond) unit else ifFalse
  @inline final def ifThenElse[R, E, A](cond: Boolean)(ifTrue: F[R, E, A], ifFalse: F[R, E, A]): F[R, E, A] = if (cond) ifTrue else ifFalse
}
