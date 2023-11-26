package izumi.functional.bio

import scala.collection.immutable.Queue

trait Applicative2[F[+_, +_]] extends Functor2[F] {
  def pure[A](a: A): F[Nothing, A]

  /** execute two operations in order, map their results */
  def map2[E, A, B, C](firstOp: F[E, A], secondOp: => F[E, B])(f: (A, B) => C): F[E, C]

  /** execute two operations in order, return result of second operation */
  def *>[E, A, B](firstOp: F[E, A], secondOp: => F[E, B]): F[E, B] = map2(firstOp, secondOp)((_, b) => b)

  /** execute two operations in order, same as `*>`, but return result of first operation */
  def <*[E, A, B](firstOp: F[E, A], secondOp: => F[E, B]): F[E, A] = map2(firstOp, secondOp)((a, _) => a)

  def traverse[E, A, B](l: Iterable[A])(f: A => F[E, B]): F[E, List[B]] = {
    map(
      l.foldLeft(pure(Queue.empty[B]): F[E, Queue[B]])((q, a) => map2(q, f(a))(_ :+ _))
    )(_.toList)
  }

  @inline final def forever[E, A](r: F[E, A]): F[E, Nothing] = *>(r, forever(r))
  def traverse_[E, A](l: Iterable[A])(f: A => F[E, Unit]): F[E, Unit] = void(traverse(l)(f))
  def sequence[E, A](l: Iterable[F[E, A]]): F[E, List[A]] = traverse(l)(identity)
  def sequence_[E](l: Iterable[F[E, Unit]]): F[E, Unit] = void(traverse(l)(identity))
  def flatTraverse[E, A, B](l: Iterable[A])(f: A => F[E, Iterable[B]]): F[E, List[B]] = map(traverse(l)(f))(_.flatten)
  def flatSequence[E, A](l: Iterable[F[E, Iterable[A]]]): F[E, List[A]] = flatTraverse(l)(identity)
  def collect[E, A, B](l: Iterable[A])(f: A => F[E, Option[B]]): F[E, List[B]] = map(traverse(l)(f))(_.flatten)
  def filter[E, A](l: Iterable[A])(f: A => F[E, Boolean]): F[E, List[A]] = collect(l)(a => map(f(a))(if (_) Some(a) else None))

  def unit: F[Nothing, Unit] = pure(())
  @inline final def traverse[E, A, B](o: Option[A])(f: A => F[E, B]): F[E, Option[B]] = o match {
    case Some(a) => map(f(a))(Some(_))
    case None => pure(None)
  }
  @inline final def when[E](cond: Boolean)(ifTrue: => F[E, Unit]): F[E, Unit] = if (cond) ifTrue else unit
  @inline final def unless[E](cond: Boolean)(ifFalse: => F[E, Unit]): F[E, Unit] = if (cond) unit else ifFalse
  @inline final def ifThenElse[E, A](cond: Boolean)(ifTrue: => F[E, A], ifFalse: => F[E, A]): F[E, A] = if (cond) ifTrue else ifFalse
}
