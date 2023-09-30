package izumi.distage.model.definition.dsl

import cats.effect.kernel.{Resource, Sync}
import izumi.distage.constructors.ZEnvConstructor
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.definition.dsl.ModuleDefDSL.DottyNothing
import izumi.distage.model.providers.Functoid
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.reflect.{Tag, TagK}
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
    implicit final def providerFromCatsProvider[F[_], A]: AdaptFunctoid.Aux[Resource[F, A], Lifecycle.FromCats[F, A]] = {
      new AdaptFunctoid[Resource[F, A]] {
        type Out = Lifecycle.FromCats[F, A]

        override def apply(a: Functoid[Resource[F, A]])(implicit tag: LifecycleTag[Lifecycle.FromCats[F, A]]): Functoid[Lifecycle.FromCats[F, A]] = {
          import tag.tagFull
          implicit val tagF: TagK[F] = tag.tagK.asInstanceOf[TagK[F]];
          val _ = tagF

          a.zip(Functoid.identity[Sync[F]])
            .map { case (resource, sync) => Lifecycle.fromCats(resource)(sync) }
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
          a.map(Lifecycle.fromZLayer(_)(zio.Tag[A]))
        }
      }
    }

  }

  trait LifecycleTag[R] {
    type F[_]
    type A

    implicit def tagFull: Tag[R]
    implicit def tagK: TagK[F]
    implicit def tagA: Tag[A]
  }

  object LifecycleTag extends LifecycleTagLowPriority {
    @inline def apply[A: LifecycleTag]: LifecycleTag[A] = implicitly

    implicit def resourceTag[R <: Lifecycle[F0, A0]: Tag, F0[_]: TagK, A0: Tag]: LifecycleTag[R with Lifecycle[F0, A0]] { type F[X] = F0[X]; type A = A0 } = {
      new LifecycleTag[R] {
        type F[X] = F0[X]
        type A = A0
        val tagK: TagK[F0] = TagK[F0]
        val tagA: Tag[A0] = Tag[A0]
        val tagFull: Tag[R] = Tag[R]
      }
    }
  }

  // FIXME wtf there's no Local3 anymore
//  trait TrifunctorHasLifecycleTag[R0, T] {
//    type F[-RR, +EE, +AA]
//    type R
//    type E
//    type A <: T
//
//    implicit def tagFull: Tag[Lifecycle[F[Any, E, _], A]]
//    implicit def ctorR: ZEnvConstructor[R]
//    implicit def ev: R0 <:< Lifecycle[F[R, E, _], A]
//    implicit def resourceTag: LifecycleTag[Lifecycle[F[Any, E, _], A]]
//  }
//
//  object TrifunctorHasLifecycleTag extends TrifunctorHasLifecycleTagLowPriority {
//
//    import scala.annotation.unchecked.uncheckedVariance as v
//
//    implicit def trifunctorResourceTag[
//      R1 <: Lifecycle[F0[R0, E0, _], A0],
//      F0[_, _, _]: TagK3,
//      R0: ZEnvConstructor,
//      E0: Tag,
//      A0 <: A1: Tag,
//      A1,
//    ]: TrifunctorHasLifecycleTag[R1 with Lifecycle[F0[R0, E0, _], A0], A1] {
//      type R = R0
//      type E = E0
//      type A = A0
//      type F[-RR, +EE, +AA] = F0[RR @v, EE @v, AA @v]
//    } = new TrifunctorHasLifecycleTag[R1, A1] { self =>
//      type F[-RR, +EE, +AA] = F0[RR @v, EE @v, AA @v]
//      type R = R0
//      type E = E0
//      type A = A0
//      val ctorR: ZEnvConstructor[R0] = implicitly
//      val tagFull: Tag[Lifecycle[F0[Any, E0, _], A0]] = implicitly
//      val ev: R1 <:< Lifecycle[F0[R0, E0, _], A0] = implicitly
//      val resourceTag: LifecycleTag[Lifecycle[F0[Any, E0, _], A0]] = new LifecycleTag[Lifecycle[F0[Any, E0, _], A0]] {
//        type F[AA] = F0[Any, E0, AA]
//        type A = A0
//        val tagFull: Tag[Lifecycle[F0[Any, E0, _], A0]] = self.tagFull
//        val tagK: TagK[F0[Any, E0, _]] = TagK[F0[Any, E0, _]]
//        val tagA: Tag[A0] = implicitly
//      }
//    }
//  }
//
//  private[definition] sealed trait TrifunctorHasLifecycleTagLowPriority extends TrifunctorHasLifecycleTagLowPriority1 {
//
//    import scala.annotation.unchecked.uncheckedVariance as v
//
//    implicit def trifunctorResourceTagNothing[
//      R1 <: Lifecycle[F0[R0, Nothing, _], A0],
//      F0[_, _, _]: TagK3,
//      R0: ZEnvConstructor,
//      A0 <: A1: Tag,
//      A1,
//    ]: TrifunctorHasLifecycleTag[R1 with Lifecycle[F0[R0, Nothing, _], A0], A1] {
//      type R = R0
//      type E = Nothing
//      type A = A0
//      type F[-RR, +EE, +AA] = F0[RR @v, EE @v, AA @v] @v
//    } = TrifunctorHasLifecycleTag.trifunctorResourceTag[R1, F0, R0, Nothing, A0, A1]
//  }

  trait ZIOEnvLifecycleTag[R0, T] {
    type R
    type E
    type A <: T

    implicit def tagFull: Tag[Lifecycle[ZIO[Any, E, _], A]]
    implicit def ctorR: ZEnvConstructor[R]
    implicit def ev: R0 <:< Lifecycle[ZIO[R, E, _], A]
    implicit def resourceTag: LifecycleTag[Lifecycle[ZIO[Any, E, _], A]]
  }

  object ZIOEnvLifecycleTag extends ZIOEnvLifecycleTagLowPriority {
    implicit def trifunctorResourceTag[
      R1 <: Lifecycle[F0[R0, E0, _], A0],
      F0[R, E, A] <: ZIO[R, E, A],
      R0: ZEnvConstructor,
      E0 >: DottyNothing: Tag,
      A0 <: A1: Tag,
      A1,
    ]: ZIOEnvLifecycleTag[R1 with Lifecycle[F0[R0, E0, _], A0], A1] {
      type R = R0
      type E = E0
      type A = A0
    } = new ZIOEnvLifecycleTag[R1, A1] { self =>
      type R = R0
      type E = E0
      type A = A0
      val ctorR: ZEnvConstructor[R0] = implicitly
      val tagFull: Tag[Lifecycle[ZIO[Any, E0, _], A0]] = implicitly
      val ev: R1 <:< Lifecycle[ZIO[R0, E0, _], A0] = implicitly
      val resourceTag: LifecycleTag[Lifecycle[ZIO[Any, E0, _], A0]] = new LifecycleTag[Lifecycle[ZIO[Any, E0, _], A0]] {
        type F[AA] = ZIO[Any, E0, AA]
        type A = A0
        val tagFull: Tag[Lifecycle[ZIO[Any, E0, _], A0]] = self.tagFull
        val tagK: TagK[ZIO[Any, E0, _]] = TagK[ZIO[Any, E0, _]]
        val tagA: Tag[A0] = implicitly
      }
    }
  }

  private[definition] sealed trait ZIOEnvLifecycleTagLowPriority extends ZIOEnvLifecycleTagLowPriority1 {
    implicit def trifunctorResourceTagNothing[
      R1 <: Lifecycle[F0[R0, Nothing, _], A0],
      F0[R, E, A] <: ZIO[R, E, A],
      R0: ZEnvConstructor,
      A0 <: A1: Tag,
      A1,
    ]: ZIOEnvLifecycleTag[R1 with Lifecycle[F0[R0, DottyNothing, _], A0], A1] {
      type R = R0
      type E = DottyNothing
      type A = A0
    } = ZIOEnvLifecycleTag.trifunctorResourceTag[R1, F0, R0, DottyNothing, A0, A1]
  }

  disableAutoTrace.discard()
}
