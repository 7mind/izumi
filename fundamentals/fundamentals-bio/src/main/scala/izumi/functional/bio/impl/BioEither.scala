package izumi.functional.bio.impl

import izumi.functional.bio.Monad2

object BioEither extends BioEither

class BioEither extends Monad2[Either] {
  override def pure[A](a: A): Either[Nothing, A] = Right(a)
  override def map[R, E, A, B](r: Either[E, A])(f: A => B): Either[E, B] = r.map(f)

  /** execute two operations in order, map their results */
  override def map2[R, E, A, B, C](firstOp: Either[E, A], secondOp: => Either[E, B])(f: (A, B) => C): Either[E, C] = {
    firstOp.flatMap(a => secondOp.map(b => f(a, b)))
  }
  override def flatMap[R, E, A, B](r: Either[E, A])(f: A => Either[E, B]): Either[E, B] = r.flatMap(f)
}

