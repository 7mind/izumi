package com.github.pshirshov.izumi.distage.model.definition

import cats.effect.{Bracket, Resource}
import com.github.pshirshov.izumi.distage.model.definition.DIResource.DIResourceUse
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity

import scala.language.implicitConversions

trait DIResource[+F[_], +OuterResource] {
  type InnerResource
  def allocate: F[InnerResource]
  def deallocate(resource: InnerResource): F[Unit]
  def extract(resource: InnerResource): OuterResource
}

trait DIResourceLowPrioritySyntax {
  implicit def ToDIResourceUse[F[_], A](resource: DIResource[F, A]): DIResourceUse[F, A] = new DIResourceUse[F, A](resource)
}

object DIResource extends DIResourceLowPrioritySyntax {
  implicit final class DIResourceUseSync[A](private val resource: DIResource.Sync[A]) extends AnyVal {
    def use[B](use: A => B): B = {
      val r = resource.allocate
      try {
        use(resource.extract(r))
      } finally resource.deallocate(r)
    }
  }

  final class DIResourceUse[F[_], A](private val resource: DIResource[F, A]) extends AnyVal {
    // FIXME: cats.Bracket
    def use[G[x] >: F[x], B](use: A => G[B])(implicit F: Bracket[G, Throwable]): G[B] = {
      F.bracket(resource.allocate)(use apply resource.extract(_))(resource.deallocate)
    }
  }

  // Lambda[A => A] instead of Identity to stop Identity from showing up in Intellij tooltips
  type Sync[+A] = DIResource[Lambda[X => X], A]

  trait Basic[F[_], A] extends DIResource[F, A] {
    override final type InnerResource = A
    override final def extract(resource: A): A = resource
  }

  trait Cats[F[_], A] extends DIResource[F, A] {
    override final type InnerResource = (A, F[Unit])
    override final def deallocate(resource: (A, F[Unit])): F[Unit] = resource._2
    override final def extract(resource: (A, F[Unit])): A = resource._1
  }

  def apply[A](acquire: => A)(release: A => Unit): DIResource.Sync[A] = {
    new Basic[Identity, A] {
      override def allocate: A = acquire
      override def deallocate(a: A): Unit = release(a)
    }
  }

  def fromAutoCloseable[A <: AutoCloseable](acquire: => A): DIResource.Sync[A] = {
    apply(acquire)(_.close)
  }

  def make[F[_], A](acquire: => F[A])(release: A => F[Unit]): DIResource[F, A] = {
    new DIResource.Basic[F, A] {
      override def allocate: F[A] = acquire
      override def deallocate(resource: A): F[Unit] = release(resource)
    }
  }

  // FIXME: pass bracket through DI
  // FIXME: implicit defs
  implicit def fromCats[F[_]: Bracket[?[_], Throwable], A](resource: Resource[F, A]): DIResource.Cats[F, A] = {
    new Cats[F, A] {
      override def allocate: F[(A, F[Unit])] = resource.allocated
    }
  }

  // FIXME: macro implementation not found
//  implicit def providerCats[F[_]: TagK, A: Tag](resourceProvider: ProviderMagnet[Resource[F, A]]): ProviderMagnet[DIResource.Cats[F, A]] = {
//    resourceProvider
//      .zip { bracket: Bracket[F, Throwable] => bracket }
//      .map { case (resource, bracket) => fromCats(resource)(bracket) }
//  }

  //  def noClose[F[_], A](acquire: => F[A]): DIResource[F, A] = apply(acquire, _ => null)
}
