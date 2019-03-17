package com.github.pshirshov.izumi.distage.model.definition

import cats.Applicative
import cats.effect.Bracket
import com.github.pshirshov.izumi.distage.model.definition.DIResource.{DIResourceBase, DIResourceUseSimple}
import com.github.pshirshov.izumi.distage.model.definition.DIResourceLowPrioritySyntax.DIResourceUse
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.{Tag, TagK}

import scala.language.experimental.macros
import scala.language.implicitConversions
import scala.reflect.macros.blackbox

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

import cats.effect.Resource

object DIResource extends DIResourceLowPrioritySyntax {

  type Simple[A] = DIResource[Lambda[X => X], A]
  type SimpleBase[+A] = DIResourceBase[Lambda[X => X], A]
  // Lambda[X => X] instead of Identity to stop Identity from showing up in Intellij tooltips

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

  trait Cats[F[_], A] extends DIResourceBase[F, A] {
    override final type InnerResource = (A, F[Unit])
    override final def deallocate(resource: (A, F[Unit])): F[Unit] = resource._2
    override final def extract(resource: (A, F[Unit])): A = resource._1
  }

  trait Mutable[+A] extends DIResourceBase[Lambda[X => X], A] with AutoCloseable {
    this: A =>
    override final type InnerResource = Unit
    override final def deallocate(resource: Unit): Unit = close()
    override final def extract(resource: Unit): A = this
  }

  implicit final class DIResourceUseSimple[F[_], A](private val resource: DIResourceBase[F, A]) extends AnyVal {
    def use[B](use: A => F[B])(implicit F: DIEffect[F]): F[B] = {
      F.bracket(acquire = resource.allocate)(release = resource.deallocate)(use = use apply resource.extract(_))
    }
  }

  trait DIResourceBase[+F[_], +OuterResource] { self =>
    type InnerResource
    def allocate: F[InnerResource]
    def deallocate(resource: InnerResource): F[Unit]
    def extract(resource: InnerResource): OuterResource

    final def map[B](f: OuterResource => B): DIResourceBase[F, B] = {
      new DIResourceBase[F, B] {
        type InnerResource = self.InnerResource
        def allocate: F[InnerResource] = self.allocate
        def deallocate(resource: InnerResource): F[Unit] = self.deallocate(resource)
        def extract(resource: InnerResource): B = f(self.extract(resource))
      }
    }

    final def flatMap[G[x] >: F[x], B](f: OuterResource => DIResourceBase[G, B])(implicit F: DIEffect[G]): DIResourceBase[G, B] = {
      import DIEffect.syntax._
      new DIResourceBase[G, B] {
        override type InnerResource = InnerResource0
        override def allocate: G[InnerResource] = {
          for {
            inner1 <- self.allocate: G[self.InnerResource]
            res2 = f(self.extract(inner1))
            inner2 <- res2.allocate
          } yield new InnerResource0 {
            def extract: B = res2.extract(inner2)
            def deallocate: G[Unit] = (self.deallocate(inner1): G[Unit]).flatMap(_ => res2.deallocate(inner2))
          }
        }
        override def deallocate(resource: InnerResource): G[Unit] = resource.deallocate
        override def extract(resource: InnerResource): B = resource.extract

        sealed trait InnerResource0 {
          def extract: B
          def deallocate: G[Unit]
        }
      }
    }
  }

  trait ResourceTag[R] {
    type F[_]
    type A
    implicit def tagFull: Tag[R]
    implicit def tagK: TagK[F]
    implicit def tagA: Tag[A]
  }

  object ResourceTag {
    def apply[A: ResourceTag]: ResourceTag[A] = implicitly

    /*implicit*/ def resourceTag[R <: DIResourceBase[F0, A0]: Tag, F0[_]: TagK, A0: Tag]: ResourceTag[R] { type F[X] = F0[X]; type A = A0 } = {
      new ResourceTag[R] {
        type F[X] = F0[X]
        type A = A0
        val tagK: TagK[F0] = TagK[F0]
        val tagA: Tag[A0] = Tag[A0]
        val tagFull: Tag[R] = Tag[R]
      }
    }

    // ^ the above should just work as an implicit but unfortunately doesn't, this macro does the same thing scala typer should've done, but manually...
    implicit def resourceTagMacro[R <: DIResourceBase[Any, Any]]: ResourceTag[R] = macro resourceTagMacroImpl[R]

    def resourceTagMacroImpl[R <: DIResourceBase[Any, Any]: c.WeakTypeTag](c: blackbox.Context): c.Expr[ResourceTag[R]] = {
      import c.universe._

      val full = weakTypeOf[R]
      val base = weakTypeOf[R].baseType(symbolOf[DIResourceBase[Any, Any]])
      val List(f, a) = base.typeArgs

      c.Expr[ResourceTag[R]](q"${reify(ResourceTag)}.resourceTag[$full, $f, $a]")
    }
  }

}

trait DIResourceLowPrioritySyntax {
  implicit def ToDIResourceUse[F[_], A](resource: DIResourceBase[F, A]): DIResourceUse[F, A] = new DIResourceUse[F, A](resource)

  /** Workaround for https://github.com/scala/bug/issues/11435 */
  implicit def ToDIResourceUseSimple[A](resource: DIResourceBase[Lambda[X => X], A]): DIResourceUseSimple[Lambda[X => X], A] = {
    new DIResourceUseSimple[Lambda[X => X], A](resource)
  }
}

private[definition] object DIResourceLowPrioritySyntax {
  final class DIResourceUse[F[_], A](private val resource: DIResourceBase[F, A]) extends AnyVal {

    def toCats[G[x] >: F[x]: Applicative]: Resource[G, A] = {
      Resource.make[G, resource.InnerResource](resource.allocate)(resource.deallocate).map(resource.extract)
    }
  }
}
