package com.github.pshirshov.izumi.distage.model.monadic

import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity

trait DIMonad[F[_]] {
  def pure[A](a: A): F[A]
  def flatMap[A, B](a: A)(f: A => F[B]): F[B]
}

object DIMonad {
  def apply[F[_]: DIMonad]: DIMonad[F] = implicitly

  implicit def fromCats[F[_]: cats.Monad]: DIMonad[F] = {
    ???
  }

  implicit val dimonadIdentity: DIMonad[Identity] = new DIMonad[Identity] {
    override def pure[A](a: A): Identity[A] = a
    override def flatMap[A, B](a: A)(f: A => Identity[B]): Identity[B] = f(a)
  }
}
