package izumi.distage.model.definition.dsl

import cats.effect.kernel.{Resource, Sync}
import izumi.distage.constructors.HasConstructor
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.providers.Functoid
import izumi.functional.bio.Local3
import izumi.reflect.{Tag, TagK, TagK3}
import zio.*

object LifecycleAdapters {
  /** Support binding various FP libraries' Resource types in `.fromResource` */
  type AdaptFunctoid[A] = Impl.AdaptFunctoidImpl[A]
  lazy val AdaptFunctoid: Impl.AdaptFunctoidImpl.type = Impl.AdaptFunctoidImpl

  type LifecycleTag[R] = Impl.LifecycleTagImpl[R]
  lazy val LifecycleTag: Impl.LifecycleTagImpl.type = Impl.LifecycleTagImpl

  type TrifunctorHasLifecycleTag[R0, T] = Impl.TrifunctorHasLifecycleTagImpl[R0, T]
  lazy val TrifunctorHasLifecycleTag: Impl.TrifunctorHasLifecycleTagImpl.type = Impl.TrifunctorHasLifecycleTagImpl

  protected[definition] object Impl {
    trait LifecycleTagImpl[R] {
      type F[_]
      type A

      implicit def tagFull: Tag[R]

      implicit def tagK: TagK[F]

      implicit def tagA: Tag[A]
    }

    object LifecycleTagImpl extends LifecycleTagLowPriority {
      @inline def apply[A: LifecycleTagImpl]: LifecycleTagImpl[A] = implicitly

      implicit def resourceTag[R <: Lifecycle[F0, A0]: Tag, F0[_]: TagK, A0: Tag]: LifecycleTagImpl[R with Lifecycle[F0, A0]] { type F[X] = F0[X]; type A = A0 } = {
        new LifecycleTagImpl[R] {
          type F[X] = F0[X]
          type A = A0
          val tagK: TagK[F0] = TagK[F0]
          val tagA: Tag[A0] = Tag[A0]
          val tagFull: Tag[R] = Tag[R]
        }
      }
    }

    /** Support binding various FP libraries' Resource types in `.fromResource` */
    trait AdaptFunctoidImpl[A] {
      type Out

      def apply(a: Functoid[A])(implicit tag: LifecycleTagImpl[Out]): Functoid[Out]
    }

    object AdaptFunctoidImpl {
      type Aux[A, B] = AdaptFunctoidImpl[A] { type Out = B }

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
      implicit final def providerFromCatsProvider[F[_], A]: AdaptFunctoidImpl.Aux[Resource[F, A], Lifecycle.FromCats[F, A]] = {
        new AdaptFunctoidImpl[Resource[F, A]] {
          type Out = Lifecycle.FromCats[F, A]

          override def apply(a: Functoid[Resource[F, A]])(implicit tag: LifecycleTagImpl[Lifecycle.FromCats[F, A]]): Functoid[Lifecycle.FromCats[F, A]] = {
            import tag.tagFull
            implicit val tagF: TagK[F] = tag.tagK.asInstanceOf[TagK[F]];
            val _ = tagF

            a.zip(Functoid.identity[Sync[F]])
              .map { case (resource, sync) => Lifecycle.fromCats(resource)(sync) }
          }
        }
      }

      /**
        * Allows you to bind [[zio.ZManaged]]-based constructor functions in `ModuleDef`:
        */
      implicit final def providerFromZIOProvider[R, E, A]: AdaptFunctoidImpl.Aux[ZManaged[R, E, A], Lifecycle.FromZIO[R, E, A]] = {
        new AdaptFunctoidImpl[ZManaged[R, E, A]] {
          type Out = Lifecycle.FromZIO[R, E, A]

          override def apply(a: Functoid[ZManaged[R, E, A]])(implicit tag: LifecycleTagImpl[Lifecycle.FromZIO[R, E, A]]): Functoid[Lifecycle.FromZIO[R, E, A]] = {
            import tag.tagFull
            a.map(Lifecycle.fromZIO)
          }
        }
      }

      /**
        * Allows you to bind [[zio.ZManaged]]-based constructor functions in `ModuleDef`:
        */
      implicit final def providerFromZLayerProvider[R, E, A: Tag]: AdaptFunctoidImpl.Aux[ZLayer[R, E, Has[A]], Lifecycle.FromZIO[R, E, A]] = {
        new AdaptFunctoidImpl[ZLayer[R, E, Has[A]]] {
          type Out = Lifecycle.FromZIO[R, E, A]

          override def apply(a: Functoid[ZLayer[R, E, Has[A]]])(implicit tag: LifecycleTagImpl[Lifecycle.FromZIO[R, E, A]]): Functoid[Lifecycle.FromZIO[R, E, A]] = {
            import tag.tagFull
            a.map(layer => Lifecycle.fromZIO(layer.map(_.get[A]).build))
          }
        }
      }

    }

    trait TrifunctorHasLifecycleTagImpl[R0, T] {
      type F[-RR, +EE, +AA]
      type R
      type E
      type A <: T

      implicit def tagLocal3: Tag[Local3[F]]

      implicit def tagFull: Tag[Lifecycle[F[Any, E, _], A]]

      implicit def ctorR: HasConstructor[R]

      implicit def ev: R0 <:< Lifecycle[F[R, E, _], A]

      implicit def resourceTag: LifecycleTagImpl[Lifecycle[F[Any, E, _], A]]
    }

    object TrifunctorHasLifecycleTagImpl extends TrifunctorHasLifecycleTagLowPriority {

      import scala.annotation.unchecked.uncheckedVariance as v

      implicit def trifunctorResourceTag[
        R1 <: Lifecycle[F0[R0, E0, _], A0],
        F0[_, _, _]: TagK3,
        R0: HasConstructor,
        E0: Tag,
        A0 <: A1: Tag,
        A1,
      ]: TrifunctorHasLifecycleTagImpl[R1 with Lifecycle[F0[R0, E0, _], A0], A1] {
        type R = R0
        type E = E0
        type A = A0
        type F[-RR, +EE, +AA] = F0[RR @v, EE @v, AA @v]
      } = new TrifunctorHasLifecycleTagImpl[R1, A1] { self =>
        type F[-RR, +EE, +AA] = F0[RR @v, EE @v, AA @v]
        type R = R0
        type E = E0
        type A = A0
        val tagLocal3: Tag[Local3[F]] = implicitly
        val ctorR: HasConstructor[R0] = implicitly
        val tagFull: Tag[Lifecycle[F0[Any, E0, _], A0]] = implicitly
        val ev: R1 <:< Lifecycle[F0[R0, E0, _], A0] = implicitly
        val resourceTag: LifecycleTagImpl[Lifecycle[F0[Any, E0, _], A0]] = new LifecycleTagImpl[Lifecycle[F0[Any, E0, _], A0]] {
          type F[AA] = F0[Any, E0, AA]
          type A = A0
          val tagFull: Tag[Lifecycle[F0[Any, E0, _], A0]] = self.tagFull
          val tagK: TagK[F0[Any, E0, _]] = TagK[F0[Any, E0, _]]
          val tagA: Tag[A0] = implicitly
        }
      }
    }

    sealed trait TrifunctorHasLifecycleTagLowPriority extends TrifunctorHasLifecycleTagLowPriority1 {

      import scala.annotation.unchecked.uncheckedVariance as v

      implicit def trifunctorResourceTagNothing[
        R1 <: Lifecycle[F0[R0, Nothing, _], A0],
        F0[_, _, _]: TagK3,
        R0: HasConstructor,
        A0 <: A1: Tag,
        A1,
      ]: TrifunctorHasLifecycleTagImpl[R1 with Lifecycle[F0[R0, Nothing, _], A0], A1] {
        type R = R0
        type E = Nothing
        type A = A0
        type F[-RR, +EE, +AA] = F0[RR @v, EE @v, AA @v] @v
      } = TrifunctorHasLifecycleTagImpl.trifunctorResourceTag[R1, F0, R0, Nothing, A0, A1]
    }

  }

}
