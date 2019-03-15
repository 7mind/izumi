package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.DIResource.DIResourceBase
import com.github.pshirshov.izumi.distage.model.definition.DIResourceLowPrioritySyntax.DIResourceUse
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.{Tag, TagK}

import scala.annotation.unchecked.uncheckedVariance
import scala.language.implicitConversions

/**
  * Example:
  * {{{
  *   resource.use {
  *     abc.cba
  *   }
  * }}}
  */
trait DIResource[+F[_], Resource] extends DIResourceBase[F, Resource] {
  def allocate: F[Resource]
  def deallocate(resource: Resource): F[Unit]

  override final def extract(resource: Resource): Resource = resource
  override final type InnerResource = Resource
}

import cats.effect.{Bracket, Resource}

object DIResource extends DIResourceLowPrioritySyntax {

  def make[F[_], A](acquire: => F[A])(release: A => F[Unit]): DIResource[F, A] = {
    new DIResource[F, A] {
      override def allocate: F[A] = acquire
      override def deallocate(resource: A): F[Unit] = release(resource)
    }
  }

  def makeSimple[A](acquire: => A)(release: A => Unit): DIResource.Simple[A] = {
    new DIResource.Simple[A] {
      override def allocate: A = acquire
      override def deallocate(a: A): Unit = release(a)
    }
  }

  def fromAutoCloseable[A <: AutoCloseable](acquire: => A): DIResource.Simple[A] = {
    makeSimple(acquire)(_.close)
  }

  // FIXME: pass bracket through DI
  // FIXME: implicit defs
  implicit def fromCats[F[_]: Bracket[?[_], Throwable], A](resource: Resource[F, A]): DIResource.Cats[F, A] = {
    new Cats[F, A] {
      override def allocate: F[(A, F[Unit])] = resource.allocated
    }
  }

  implicit def providerCats[F[_]: TagK, A: Tag](resourceProvider: ProviderMagnet[Resource[F, A]]): ProviderMagnet[DIResource.Cats[F, A]] = {
    resourceProvider
      .zip(ProviderMagnet.identity[Bracket[F, Throwable]])
      .map { case (resource, bracket) => fromCats(resource)(bracket) }
  }

  type Simple[A] = DIResource[Lambda[X => X], A] // Lambda[X => X] instead of Identity to stop Identity from showing up in Intellij tooltips

  trait DIResourceBase[+F[_], +OuterResource] {
    type InnerResource
    def allocate: F[InnerResource]
    def deallocate(resource: InnerResource): F[Unit]
    def extract(resource: InnerResource): OuterResource
  }

  trait Cats[F[_], A] extends DIResourceBase[F, A] {
    override final type InnerResource = (A, F[Unit])
    override final def deallocate(resource: (A, F[Unit])): F[Unit] = resource._2
    override final def extract(resource: (A, F[Unit])): A = resource._1
  }

  trait Mutable[+A] extends DIResourceBase[Lambda[X => X], A] with AutoCloseable {
    this: A =>
    override type InnerResource = Unit
    override final def deallocate(resource: Unit): Unit = close()
    override final def extract(resource: Unit): A = this
  }

  implicit final class DIResourceUseSimple[A](private val resource: DIResource.Simple[A]) extends AnyVal {
    def use[B](use: A => B): B = {
      val r = resource.allocate
      try {
        use(resource.extract(r))
      } finally resource.deallocate(r)
    }
  }

  trait ResourceTag[R] {
    type F[_]
    type A
    implicit def tagK: TagK[F]
    implicit def tagA: Tag[A]
    implicit def tagR: Tag[R]  @uncheckedVariance
  }

  object ResourceTag {
    def apply[A: ResourceTag]: ResourceTag[A] = implicitly

    implicit def resourceTag[R <: DIResourceBase[F0, A0]: Tag, F0[_]: TagK, A0: Tag]: ResourceTag[R] { type F[X] = F0[X]; type A = A0 } = {
      new ResourceTag[R] {
        type F[X] = F0[X]
        type A = A0
        val tagK: TagK[F0] = TagK[F0]
        val tagA: Tag[A0] = Tag[A0]
        val tagR: Tag[R] = Tag[R]
      }
    }
//    implicit def resourceTag[F0[_]: TagK, A0: Tag]: ResourceTag[DIResourceBase[F0, A0]] { type F[X] = F0[X]; type A = A0 } = {
//      new ResourceTag[DIResourceBase[F0, A0]] {
//        type F[X] = F0[X]
//        type A = A0
//        val tagK: TagK[F0] = TagK[F0]
//        val tagA: Tag[A0] = Tag[A0]
//        val tagR: Tag[DIResourceBase[F0, A0]] = Tag[DIResourceBase[F0, A0]]
//      }
//    }
  }

}

trait DIResourceLowPrioritySyntax {
  implicit def ToDIResourceUse[F[_], A](resource: DIResource[F, A]): DIResourceUse[F, A] = new DIResourceUse[F, A](resource)
}

private[definition] object DIResourceLowPrioritySyntax {
  final class DIResourceUse[F[_], A](private val resource: DIResource[F, A]) extends AnyVal {
    // FIXME: cats.Bracket
    def use[G[x] >: F[x], B](use: A => G[B])(implicit F: Bracket[G, Throwable]): G[B] = {
      F.bracket(resource.allocate)(use apply resource.extract(_))(resource.deallocate)
    }
  }
}
