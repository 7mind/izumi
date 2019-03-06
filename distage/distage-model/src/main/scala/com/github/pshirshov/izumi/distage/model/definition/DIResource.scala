package com.github.pshirshov.izumi.distage.model.definition

import cats.effect.{Bracket, Resource}
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity

import scala.language.implicitConversions

trait DIResource[+F[_], A] {
  def allocate: F[A]
  def deallocate(a: A): F[Unit]
}

object DIResource {
  // Lambda[A => A] instead of Identity to stop Identity from showing up in Intellij tooltips
  trait Sync[A] extends DIResource[Lambda[A => A], A]

  trait Cats[F[_], A] extends DIResource[F, (A, F[Unit])] {
    override final def deallocate(a: (A, F[Unit])): F[Unit] = a._2
  }

  def apply[A](acquire: => A)(release: A => Unit): DIResource[Identity, A] = {
    new Sync[A] {
      override def allocate: A = acquire

      override def deallocate(a: A): Unit = release(a)
    }
  }

  def fromAutoCloseable[A <: AutoCloseable](acquire: => A): DIResource[Identity, A] = {
    apply(acquire)(_.close)
  }

  def make[F[_], A](acquire: => F[A])(release: A => F[Unit]): DIResource[F, A] = {
    new DIResource[F, A] {
      override def allocate: F[A] = acquire
      override def deallocate(a: A): F[Unit] = release(a)
    }
  }

  implicit def fromCats[F[_]: Bracket[?[_], Throwable], A](resource: Resource[F, A]): DIResource.Cats[F, A] = {
    new Cats[F, A] {
      override def allocate: F[(A, F[Unit])] = resource.allocated
    }
  }

  implicit def providerCats[F[_], A](resourceProvider: ProviderMagnet[Resource[F, A]]): ProviderMagnet[DIResource.Cats[F, A]] = {
    resourceProvider.map[DIResource[F, A]](fromCats(_))
  }

  //  def noClose[F[_], A](acquire: => F[A]): DIResource[F, A] = apply(acquire, _ => null)
}
