package izumi.functional.bio.impl

import izumi.functional.bio.Monad2
import izumi.fundamentals.platform.functional.Identity2

object BioIdentity2 extends BioIdentity2

open class BioIdentity2 extends Monad2[Identity2] {

  override def pure[A](a: A): Identity2[Nothing, A] = {
    a
  }
  override def flatMap[E, A, B](r: Identity2[E, A])(f: A => Identity2[E, B]): Identity2[E, B] = {
    f(r)
  }
  override def map2[E, A, B, C](firstOp: Identity2[E, A], secondOp: => Identity2[E, B])(f: (A, B) => C): Identity2[E, C] = {
    f(firstOp, secondOp)
  }
  override def *>[E, A, B](firstOp: Identity2[E, A], secondOp: => Identity2[E, B]): Identity2[E, B] = {
    val _ = firstOp; secondOp
  }
  override def <*[E, A, B](firstOp: Identity2[E, A], secondOp: => Identity2[E, B]): Identity2[E, A] = {
    secondOp; firstOp
  }
  override def traverse[E, A, B](l: Iterable[A])(f: A => Identity2[E, B]): Identity2[E, List[B]] = {
    l.map(f).toList
  }
  override def map[E, A, B](r: Identity2[E, A])(f: A => B): Identity2[E, B] = {
    f(r)
  }
}
