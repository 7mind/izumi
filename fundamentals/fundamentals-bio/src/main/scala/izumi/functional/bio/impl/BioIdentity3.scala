package izumi.functional.bio.impl

import izumi.functional.bio.Monad3
import izumi.fundamentals.platform.functional.Identity3

object BioIdentity3 extends BioIdentity3

class BioIdentity3 extends Monad3[Identity3] {
  override def pure[A](a: A): Identity3[Any, Nothing, A] = {
    a
  }
  override def flatMap[R, E, A, B](r: Identity3[R, E, A])(f: A => Identity3[R, E, B]): Identity3[R, E, B] = {
    f(r)
  }
  override def map2[R, E, A, B, C](firstOp: Identity3[R, E, A], secondOp: => Identity3[R, E, B])(f: (A, B) => C): Identity3[R, E, C] = {
    f(firstOp, secondOp)
  }
  override def *>[R, E, A, B](firstOp: Identity3[R, E, A], secondOp: => Identity3[R, E, B]): Identity3[R, E, B] = {
    val _ = firstOp; secondOp
  }
  override def <*[R, E, A, B](firstOp: Identity3[R, E, A], secondOp: => Identity3[R, E, B]): Identity3[R, E, A] = {
    secondOp; firstOp
  }
  override def traverse[R, E, A, B](l: Iterable[A])(f: A => Identity3[R, E, B]): Identity3[R, E, List[B]] = {
    l.map(f).toList
  }
  override def map[R, E, A, B](r: Identity3[R, E, A])(f: A => B): Identity3[R, E, B] = {
    f(r)
  }
}
