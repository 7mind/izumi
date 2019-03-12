package com.github.pshirshov.izumi.distage.model.monadic

import cats.effect.IO
import cats.instances.list.catsStdInstancesForList
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity

trait DIMonad[F[_]] {
  def pure[A](a: A): F[A]
  def map[A, B](fa: F[A])(f: A => B): F[B]
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
  // FIXME: tailRecM?

  final val unit: F[Unit] = pure(())
  /***/
  // FIXME: hack shit[ what about error recovery? `Id` can't implement MonadError ]
  final def maybeSuspend[A](eff: => A): F[A] = map(unit)(_ => eff)

  final def widen[A, B >: A](fa: F[A]): F[B] = fa.asInstanceOf[F[B]]
  final def traverse_[A](l: Iterable[A])(f: A => F[Unit]): F[Unit] = {
    l.foldLeft[F[Unit]](unit) { (acc, a) =>
      flatMap(acc)(_ => f(a))
    }
  }
  final def foldLeftM[S, A](in: Iterable[A])(zero: S)(f: (S, A) => F[S]): F[S] =
    in.foldLeft[F[S]](pure(zero)) { (acc, a) =>
      flatMap(acc)(f(_, a))
    }
}

object DIMonad
  extends FromCats {

  def apply[F[_]: DIMonad]: DIMonad[F] = implicitly

  implicit val diMonadIdentity: DIMonad[Identity] = new DIMonad[Identity] {
    override def pure[A](a: A): Identity[A] = a
    override def flatMap[A, B](a: A)(f: A => Identity[B]): Identity[B] = f(a)
    override def map[A, B](fa: Identity[A])(f: A => B): Identity[B] = f(fa)
  }

//  def x = fromCats[Chain, Monad[Chain]]
//  def x = fromCats[Chain, Monad]
//  def x = DIMonad[List](fromCats(J.j,catsStdInstancesForList))
  def x = DIMonad[List]
}

trait FromCats {

  implicit def fromCats[F[_], R[_[_]]](implicit l: J[R], F0: R[F]): DIMonad[F] = new DIMonad[F] {
    val F = F0.asInstanceOf[cats.Monad[F]]
    override def pure[A](a: A): F[A] = F.pure(a)
    override def map[A, B](fa: F[A])(f: A => B): F[B] = F.map(fa)(f)
    override def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B] = F.flatMap(fa)(f)
  }

  class J[R[_[_]]]// { type Out = cats.Monad[Vector] }
  object J {
//    implicit val j: J { type Out = cats.Monad[Vector] } = new J
    implicit val j: J[cats.Monad] = new J
  }

}
