package izumi.distage.model.definition.dsl

import cats.effect.kernel.{Resource, Sync}
import izumi.distage.constructors.ZEnvConstructor
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.definition.dsl.ModuleDefDSL.DottyNothing
import izumi.distage.model.providers.Functoid
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.reflect.{Tag, TagK, TagKK}
import zio.*
import zio.managed.ZManaged
import zio.stacktracer.TracingImplicits.disableAutoTrace

object LifecycleAdapters {

  /** Support binding various FP libraries' Resource types in `.fromResource` */
  trait AdaptFunctoid[A] {
    type Out

    def apply(a: Functoid[A])(implicit tag: LifecycleTag[Out]): Functoid[Out]
  }

  object AdaptFunctoid {
    type Aux[A, B] = AdaptFunctoid[A] { type Out = B }

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
      *     make[ExecutionContext].from(scala.concurrent.ExecutionContext.global)
      *
      *     make[JdbcConfig].from {
      *       conf: JdbcConfig @ConfPath("jdbc") => conf
      *     }
      *
      *     make[HikariTransactor[IO]].fromResource {
      *       (ec: ExecutionContext, jdbc: JdbcConfig) =>
      *         implicit val contextShift: ContextShift[IO] = IO.contextShift(ec)
      *
      *         HikariTransactor.newHikariTransactor[IO](jdbc.driverClassName, jdbc.url, jdbc.user, jdbc.pass, ec, ec)
      *     }
      *   }
      * }}}
      *
      * @note binding a cats Resource[F, A] will add a
      *       dependency on `Sync[F]` for your corresponding `F` type
      *       (`Sync[F]` instance will generally be provided automatically via [[izumi.distage.modules.DefaultModule]])
      */
    implicit final def providerFromCatsProvider[F[_]: TagK, A]: AdaptFunctoid.Aux[Resource[F, A], Lifecycle.FromCats[F, A]] = {
      new AdaptFunctoid[Resource[F, A]] {
        type Out = Lifecycle.FromCats[F, A]

        override def apply(a: Functoid[Resource[F, A]])(implicit tag: LifecycleTag[Lifecycle.FromCats[F, A]]): Functoid[Lifecycle.FromCats[F, A]] = {
          import tag.tagFull

          a.zip(Functoid.identity[Sync[F]])
            .map { case (resource, sync) => Lifecycle.fromCats(resource)(using sync) }
        }
      }
    }

    /**
      * Allows you to bind Scoped [[zio.ZIO]]-based constructor functions in `ModuleDef`:
      *
      * @note due to limitations of Scala 2 type inference, only plain `Scope` environment is supported here.
      *       if you need to inject ZIO with other types use [[ModuleDefDSL.MakeDSLBase.MakeFromZIOZEnv#fromZIOEnv fromZIOEnv]]
      *       method instead of `fromResource`.
      */
    implicit final def providerFromZIOScopedProvider[E, A]: AdaptFunctoid.Aux[ZIO[Scope, E, A], Lifecycle.FromZIO[Any, E, A]] = {
      new AdaptFunctoid[ZIO[Scope, E, A]] {
        type Out = Lifecycle.FromZIO[Any, E, A]

        override def apply(
          a: Functoid[ZIO[Scope, E, A]]
        )(implicit tag: LifecycleTag[Lifecycle.FromZIO[Any, E, A]]
        ): Functoid[Lifecycle.FromZIO[Any, E, A]] = {
          import tag.tagFull
          a.map(Lifecycle.fromZIO[Any](_))
        }
      }
    }

    /**
      * Allows you to bind [[zio.managed.ZManaged]]-based constructor functions in `ModuleDef`:
      */
    implicit final def providerFromZManagedProvider[R, E, A]: AdaptFunctoid.Aux[ZManaged[R, E, A], Lifecycle.FromZIO[R, E, A]] = {
      new AdaptFunctoid[ZManaged[R, E, A]] {
        type Out = Lifecycle.FromZIO[R, E, A]

        override def apply(a: Functoid[ZManaged[R, E, A]])(implicit tag: LifecycleTag[Lifecycle.FromZIO[R, E, A]]): Functoid[Lifecycle.FromZIO[R, E, A]] = {
          import tag.tagFull
          a.map(Lifecycle.fromZManaged(_))
        }
      }
    }

    /**
      * Allows you to bind [[zio.managed.ZManaged]]-based constructor functions in `ModuleDef`:
      */
    implicit final def providerFromZLayerProvider[R, E, A: Tag]: AdaptFunctoid.Aux[ZLayer[R, E, A], Lifecycle.FromZIO[R, E, A]] = {
      new AdaptFunctoid[ZLayer[R, E, A]] {
        type Out = Lifecycle.FromZIO[R, E, A]

        override def apply(a: Functoid[ZLayer[R, E, A]])(implicit tag: LifecycleTag[Lifecycle.FromZIO[R, E, A]]): Functoid[Lifecycle.FromZIO[R, E, A]] = {
          import tag.tagFull
          a.map(Lifecycle.fromZLayer(_)(using zio.Tag[A]))
        }
      }
    }

  }

  /** Marker carrying the type-tag information needed to bind a `Lifecycle`-shaped `R` in `ModuleDef`.
    *
    * Bifunctor-shaped after M5: `F` is `[+_, +_]`, the `E` channel carries the lifecycle's typed error
    * type, and `A` is the resource value type. `tagK` is a [[TagKK]] for the bifunctor effect type.
    */
  trait LifecycleTag[R] {
    type F[+_, +_]
    type E
    type A

    implicit def tagFull: Tag[R]
    implicit def tagK: TagKK[F]
    implicit def tagE: Tag[E]
    implicit def tagA: Tag[A]
  }

  object LifecycleTag extends LifecycleTagLowPriority {
    @inline def apply[A: LifecycleTag]: LifecycleTag[A] = implicitly

    implicit def resourceTag[R <: Lifecycle[F0, E0, A0]: Tag, F0[+_, +_]: TagKK, E0: Tag, A0: Tag]: LifecycleTag[R & Lifecycle[F0, E0, A0]] {
      type F[+e, +a] = F0[e, a]; type E = E0; type A = A0
    } = {
      new LifecycleTag[R] {
        type F[+e, +a] = F0[e, a]
        type E = E0
        type A = A0
        val tagK: TagKK[F0] = TagKK[F0]
        val tagE: Tag[E0] = Tag[E0]
        val tagA: Tag[A0] = Tag[A0]
        val tagFull: Tag[R] = Tag[R]
      }
    }
  }

  trait ZIOEnvLifecycleTag[R0, T] {
    type R
    type E
    type A <: T

    implicit def tagFull: Tag[Lifecycle[ZIO[Any, +_, +_], E, A]]
    implicit def ctorR: ZEnvConstructor[R]
    implicit def ev: R0 <:< Lifecycle[ZIO[R, +_, +_], E, A]
    implicit def resourceTag: LifecycleTag[Lifecycle[ZIO[Any, +_, +_], E, A]]
  }

  object ZIOEnvLifecycleTag extends ZIOEnvLifecycleTagLowPriority {
    implicit def trifunctorResourceTag[
      R1 <: Lifecycle[λ[(`+e`, `+a`) => F0[R0, e, a]], E0, A0],
      F0[-R, +E, +A] <: ZIO[R, E, A],
      R0: ZEnvConstructor,
      E0 >: DottyNothing: Tag,
      A0 <: A1: Tag,
      A1,
    ]: ZIOEnvLifecycleTag[R1 & Lifecycle[λ[(`+e`, `+a`) => F0[R0, e, a]], E0, A0], A1] {
      type R = R0
      type E = E0
      type A = A0
    } = new ZIOEnvLifecycleTag[R1, A1] { self =>
      type R = R0
      type E = E0
      type A = A0
      val ctorR: ZEnvConstructor[R0] = implicitly
      val tagFull: Tag[Lifecycle[ZIO[Any, +_, +_], E0, A0]] = implicitly
      val ev: R1 <:< Lifecycle[ZIO[R0, +_, +_], E0, A0] =
        implicitly[Any <:< Any].asInstanceOf[R1 <:< Lifecycle[ZIO[R0, +_, +_], E0, A0]]
      val resourceTag: LifecycleTag[Lifecycle[ZIO[Any, +_, +_], E0, A0]] = new LifecycleTag[Lifecycle[ZIO[Any, +_, +_], E0, A0]] {
        type F[+e, +a] = ZIO[Any, e, a]
        type E = E0
        type A = A0
        val tagFull: Tag[Lifecycle[ZIO[Any, +_, +_], E0, A0]] = self.tagFull
        val tagK: TagKK[ZIO[Any, +_, +_]] = TagKK[ZIO[Any, +_, +_]]
        val tagE: Tag[E0] = implicitly
        val tagA: Tag[A0] = implicitly
      }
    }

    disableAutoTrace.discard()
  }

  private[definition] sealed trait ZIOEnvLifecycleTagLowPriority extends ZIOEnvLifecycleTagLowPriority1 {
    implicit def trifunctorResourceTagNothing[
      R1 <: Lifecycle[λ[(`+e`, `+a`) => F0[R0, e, a]], Nothing, A0],
      F0[-R, +E, +A] <: ZIO[R, E, A],
      R0: ZEnvConstructor,
      A0 <: A1: Tag,
      A1,
    ]: ZIOEnvLifecycleTag[R1 & Lifecycle[λ[(`+e`, `+a`) => F0[R0, e, a]], DottyNothing, A0], A1] {
      type R = R0
      type E = DottyNothing
      type A = A0
    } = ZIOEnvLifecycleTag.trifunctorResourceTag[R1, F0, R0, DottyNothing, A0, A1]
  }

}
