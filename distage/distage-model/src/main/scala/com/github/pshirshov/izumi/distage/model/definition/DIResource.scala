package com.github.pshirshov.izumi.distage.model.definition

import cats.effect.Resource
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity

trait DIResource[+F[_], A] {
  def allocate: F[A]
  def deallocate(a: A): F[Unit]
}

object DIResource {
  trait Sync[A] extends DIResource[Lambda[A => A], A]

  def apply[A](acquire: => A, release: A => Unit): DIResource[Identity, A] = new Sync[A] {
    override def allocate: A = acquire
    override def deallocate(a: A): Unit = release(a)
  }

  def fromAutoCloseable[A <: AutoCloseable](acquire: => A): DIResource[Identity, A] = apply(acquire, _.close)

  def make[F[_], A](acquire: => F[A], release: A => F[Unit]): DIResource[F, A] = new DIResource[F, A] {
    override def allocate: F[A] = acquire
    override def deallocate(a: A): F[Unit] = release(a)
  }

  implicit def fromCats[F[_], A](resource: Resource[F, A]): DIResource[F, A] = {
  }

  implicit def providerFromCats[F[_], A](resourceProvider: ProviderMagnet[Resource[F, A]]): ProviderMagnet[DIResource[F, A]] = {
    resourceProvider.map[DIResource[F, A]](fromCats(_))
  }

  //  def noClose[F[_], A](acquire: => F[A]): DIResource[F, A] = apply(acquire, _ => null)
}
