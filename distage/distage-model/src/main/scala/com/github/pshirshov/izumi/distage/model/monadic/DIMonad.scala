package com.github.pshirshov.izumi.distage.model.monadic

import cats.Monad
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity

trait DIMonad[F[_]] {
  def pure[A](a: A): F[A]
  def map[A, B](fa: F[A])(f: A => B): F[B]
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
}

object DIMonad {
  def apply[F[_]: DIMonad]: DIMonad[F] = implicitly

  implicit def fromCats[F[_]](implicit F: Monad[F]): DIMonad[F] = new DIMonad[F] {
    override def pure[A](a: A): F[A] = F.pure(a)
    override def map[A, B](fa: F[A])(f: A => B): F[B] = F.map(fa)(f)
    override def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B] = F.flatMap(fa)(f)
  }

  @noinline def xyz(i: Int): String = i.to(5).toString()

  implicit val diMonadIdentity: DIMonad[Identity] = new DIMonad[Identity] {
    override def pure[A](a: A): Identity[A] = a
    override def flatMap[A, B](a: A)(f: A => Identity[B]): Identity[B] = f(a)
    override def map[A, B](fa: Identity[A])(f: A => B): Identity[B] = f(fa)
  }
}
