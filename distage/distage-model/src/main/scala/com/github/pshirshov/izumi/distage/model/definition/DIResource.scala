package com.github.pshirshov.izumi.distage.model.definition

import cats.Applicative
import cats.effect.Bracket
import com.github.pshirshov.izumi.distage.model.definition.DIResource.DIResourceBase
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.{Tag, TagK}
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity

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
  def acquire: F[Resource]
  def release(resource: Resource): F[Unit]

  override final def extract(resource: Resource): Resource = resource
  override final type InnerResource = Resource
}

object DIResource {
  import cats.effect.Resource

  def make[F[_], A](acquire: => F[A])(release: A => F[Unit]): DIResource[F, A] = {
    def a = acquire
    def r = release
    new DIResource[F, A] {
      override def acquire: F[A] = a
      override def release(resource: A): F[Unit] = r(resource)
    }
  }

  def makeSimple[A](acquire: => A)(release: A => Unit): DIResource[Identity, A] = {
    def a = acquire
    def r = release
    new DIResource.Simple[A] {
      override def acquire: A = a
      override def release(a: A): Unit = r(a)
    }
  }

  def fromAutoCloseable[A <: AutoCloseable](acquire: => A): DIResource[Identity, A] = {
    makeSimple(acquire)(_.close)
  }

  trait Simple[A] extends DIResource[Identity, A]

  trait Mutable[+A] extends DIResourceBase[Identity, A] with AutoCloseable {
    this: A =>
    override final type InnerResource = Unit
    override final def release(resource: Unit): Unit = close()
    override final def extract(resource: Unit): A = this
  }

  trait Cats[F[_], A] extends DIResourceBase[F, A] {
    override final type InnerResource = (A, F[Unit])
    override final def release(resource: (A, F[Unit])): F[Unit] = resource._2
    override final def extract(resource: (A, F[Unit])): A = resource._1
  }

  implicit final class DIResourceUse[F[_], A](private val resource: DIResourceBase[F, A]) extends AnyVal {
    /**
      * Please enable `-Xsource:2.13` compiler option
      * if you're having trouble invoking this method
      * on `Identity` Resources. Alternatively, add
      * an explicit type ascription to your `resource`
      * as in:
      *
      * {{{
      *   (resource: Resource[Identity, MyRes]).use { ... }
      * }}}
      *
      * @see bug https://github.com/scala/bug/issues/11435 for details
      *     [FIXED in 2.13 and in 2.12 with `-Xsource:2.13` flag]
      */
    def use[B](use: A => F[B])(implicit F: DIEffect[F]): F[B] = {
      F.bracket(acquire = resource.acquire)(release = resource.release)(
        use = a => F.flatMap(F.maybeSuspend(resource.extract(a)))(use)
      )
    }
  }

  implicit final class DIResourceCatsSyntax[F[_], A](private val resource: DIResourceBase[F, A]) extends AnyVal {
    def toCats[G[x] >: F[x]: Applicative]: Resource[G, A] = {
      Resource.make[G, resource.InnerResource](resource.acquire)(resource.release).map(resource.extract)
    }
  }

  trait DIResourceBase[+F[_], +OuterResource] { self =>
    type InnerResource
    def acquire: F[InnerResource]
    def release(resource: InnerResource): F[Unit]
    def extract(resource: InnerResource): OuterResource

    final def map[B](f: OuterResource => B): DIResourceBase[F, B] = mapImpl(this)(f)
    final def flatMap[G[x] >: F[x]: DIEffect, B](f: OuterResource => DIResourceBase[G, B]): DIResourceBase[G, B] = flatMapImpl[G, OuterResource, B](this)(f)
    final def evalMap[G[x] >: F[x]: DIEffect, B](f: OuterResource => G[B]): DIResourceBase[G, B] = evalMapImpl[G, OuterResource, B](this)(f)
  }

  def fromCats[F[_]: Bracket[?[_], Throwable], A](resource: Resource[F, A]): DIResource.Cats[F, A] = {
    new Cats[F, A] {
      override def acquire: F[(A, F[Unit])] = resource.allocated
    }
  }

  /**
    * Allows you to bind [[cats.effect.Resource]]-based constructors in `ModuleDef`:
    *
    * Example:
    * {{{
    *   import cats.effect._
    *
    *   val catsResource = Resource.liftF(IO(5))
    *
    *   val module = new distage.ModuleDef {
    *
    *     make[Int].fromResource(catsResource)
    *
    *     addImplicit[Bracket[IO, Throwable]]
    *   }
    * }}}
    *
    * NOTE: binding a cats Resource[F, A] will add a
    *       dependency on `Bracket[F, Throwable]` for
    *       your corresponding `F` type
    */
  implicit def providerFromCats[F[_]: TagK, A: Tag](resource: Resource[F, A]): ProviderMagnet[DIResource.Cats[F, A]] = {
    providerFromCatsProvider(ProviderMagnet.pure(resource))
  }

  /**
    * Allows you to bind [[cats.effect.Resource]]-based constructor functions in `ModuleDef`:
    *
    * Example:
    * {{{
    *   import cats.effect._
    *   import doobie.hikari._
    *
    *   final case class JdbcConfig(driverClassName: String, url: String, user: String, pass: String)
    *
    *   val module = new distage.ModuleDef {
    *
    *     make[ExecutionContext].from(scala.concurrent.ExecutionContext.global)
    *
    *     make[JdbcConfig].from {
    *       conf: JdbcConfig @ConfPath("jdbc") => conf
    *     }
    *
    *     make[HikariTransactor[IO]].fromResource {
    *       (ec: ExecutionContext, jdbc: JdbcConfig) =>
    *         implicit val C: ContextShift[IO] = IO.contextShift(ec)
    *
    *         HikariTransactor.newHikariTransactor[IO](jdbc.driverClassName, jdbc.url, jdbc.user, jdbc.pass, ec, ec)
    *     }
    *
    *     addImplicit[Bracket[IO, Throwable]]
    *   }
    * }}}
    *
    * NOTE: binding a cats Resource[F, A] will add a
    *       dependency on `Bracket[F, Throwable]` for
    *       your corresponding `F` type
    */
  implicit def providerFromCatsProvider[F[_]: TagK, A: Tag](resourceProvider: ProviderMagnet[Resource[F, A]]): ProviderMagnet[DIResource.Cats[F, A]] = {
    resourceProvider
      .zip(ProviderMagnet.identity[Bracket[F, Throwable]])
      .map { case (resource, bracket) => fromCats(resource)(bracket) }
  }

  private[this] final def mapImpl[F[_], A, B](self: DIResourceBase[F, A])(f: A => B): DIResourceBase[F, B] = {
    new DIResourceBase[F, B] {
      type InnerResource = self.InnerResource
      def acquire: F[InnerResource] = self.acquire
      def release(resource: InnerResource): F[Unit] = self.release(resource)
      def extract(resource: InnerResource): B = f(self.extract(resource))
    }
  }

  private[this] final def flatMapImpl[F[_], A, B](self: DIResourceBase[F ,A])(f: A => DIResourceBase[F, B])(implicit F: DIEffect[F]): DIResourceBase[F, B] = {

    def bracketOnError[a, b](acquire: => F[a])(releaseOnError: a => F[Unit])(use: a => F[b]): F[b] = {
      F.bracketCase(acquire = acquire)(release = {
        case (a, Some(_)) => releaseOnError(a)
        case _ => F.unit
      })(use = use)
    }
    import DIEffect.syntax._

    new DIResourceBase[F, B] {
      override type InnerResource = InnerResource0
      override def acquire: F[InnerResource] = {
        bracketOnError(self.acquire)(self.release) {
          inner1 =>
            val res2 = f(self.extract(inner1))
            bracketOnError(res2.acquire)(res2.release) {
              inner2 =>
                F.pure(new InnerResource0 {
                  def extract: B = res2.extract(inner2)
                  def deallocate: F[Unit] = {
                    F.unit
                      .guarantee(self.release(inner1))
                      .guarantee(res2.release(inner2))
                  }
                })
            }
        }
      }
      override def release(resource: InnerResource): F[Unit] = resource.deallocate
      override def extract(resource: InnerResource): B = resource.extract

      sealed trait InnerResource0 {
        def extract: B
        def deallocate: F[Unit]
      }
    }
  }

  private[this] final def evalMapImpl[F[_], A, B](self: DIResourceBase[F, A])(f: A => F[B])(implicit F: DIEffect[F]): DIResourceBase[F, B] = {
    flatMapImpl(self)(a => DIResource.make(f(a))(_ => F.unit))
  }

  trait ResourceTag[R] {
    type F[_]
    type A
    implicit def tagFull: Tag[R]
    implicit def tagK: TagK[F]
    implicit def tagA: Tag[A]
  }

  object ResourceTag extends ResourceTagLowPriority {
    def apply[A: ResourceTag]: ResourceTag[A] = implicitly

    implicit def resourceTag[R <: DIResourceBase[F0, A0] : Tag, F0[_] : TagK, A0: Tag]: ResourceTag[R with DIResourceBase[F0, A0]] {type F[X] = F0[X]; type A = A0} = {
      new ResourceTag[R] {
        type F[X] = F0[X]
        type A = A0
        val tagK: TagK[F0] = TagK[F0]
        val tagA: Tag[A0] = Tag[A0]
        val tagFull: Tag[R] = Tag[R]
      }
    }

    def fakeResourceTagMacroIntellijWorkaroundImpl[R <: DIResourceBase[Any, Any]: c.WeakTypeTag](c: blackbox.Context): c.Expr[ResourceTag[R]] = {
      c.abort(c.enclosingPosition, s"could not find implicit ResourceTag for ${c.universe.weakTypeOf[R]}!")
    }
  }

  trait ResourceTagLowPriority {
    /**
      * The `resourceTag` implicit above works perfectly fine, this macro here is exclusively
      * a workaround for highlighting in Intellij IDEA
      * TODO: include link to IJ bug tracker
      */
    implicit final def fakeResourceTagMacroIntellijWorkaround[R <: DIResourceBase[Any, Any]]: ResourceTag[R] = macro ResourceTag.fakeResourceTagMacroIntellijWorkaroundImpl[R]

////    ^ the above should just work as an implicit but unfortunately doesn't, this macro does the same thing scala typer should've done, but manually...
//    def resourceTagMacroImpl[R <: DIResourceBase[Any, Any]: c.WeakTypeTag](c: blackbox.Context): c.Expr[ResourceTag[R]] = {
//      import c.universe._
//
//      val full = weakTypeOf[R].widen
//      val base = weakTypeOf[R].baseType(symbolOf[DIResourceBase[Any, Any]])
//      val List(f, a) = base.typeArgs
//
//      c.Expr[ResourceTag[R]](q"${reify(ResourceTag)}.resourceTag[$full, $f, $a]")
//    }
  }

}
