package izumi.distage.model.providers

import izumi.fundamentals.platform.language.Quirks.Discarder
import zio.managed.ZManaged
import zio.stacktracer.TracingImplicits.disableAutoTrace

private[providers] trait FunctoidLifecycleAdapters {

  import cats.effect.kernel.{Resource, Sync}
  import izumi.functional.lifecycle.Lifecycle
  import izumi.reflect.{Tag, TagK}
  import zio.*

  import scala.language.implicitConversions

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
    *     make[Int].fromResource(catsResource)
    *   }
    * }}}
    *
    * @note binding a cats Resource[F, A] will add a
    *       dependency on `Sync[F]` for your corresponding `F` type
    *       (`Sync[F]` instance will generally be provided automatically via [[izumi.distage.modules.DefaultModule]])
    */
  implicit final def providerFromCats[F[_]: TagK, A](
    resource: => Resource[F, A]
  )(implicit tag: Tag[Lifecycle.FromCats[F, A]]
  ): Functoid[Lifecycle.FromCats[F, A]] = {
    Functoid.identity[Sync[F]].map {
      implicit sync: Sync[F] =>
        Lifecycle.fromCats(resource)(sync)
    }
  }

  /**
    * Allows you to bind Scoped [[zio.ZIO]]-based constructors in `ModuleDef`:
    */
  implicit final def providerFromZIOScoped[R, E, A](
    scoped: => ZIO[Scope & R, E, A]
  )(implicit tag: Tag[Lifecycle.FromZIO[R, E, A]]
  ): Functoid[Lifecycle.FromZIO[R, E, A]] = {
    Functoid.lift(Lifecycle.fromZIO[R](scoped))
  }

  /**
    * Allows you to bind Scoped [[zio.ZIO]]-based constructors in `ModuleDef`:
    */
  // workaround for inference issues with `E=Nothing`, scalac error: Couldn't find Tag[FromZIO[Any, E, Clock]] when binding ZManaged[Any, Nothing, Clock]
  implicit final def providerFromZIOScopedNothing[R, A](
    scoped: => ZIO[Scope & R, Nothing, A]
  )(implicit tag: Tag[Lifecycle.FromZIO[R, Nothing, A]]
  ): Functoid[Lifecycle.FromZIO[R, Nothing, A]] = {
    Functoid.lift(Lifecycle.fromZIO[R](scoped))
  }

  /**
    * Allows you to bind [[zio.managed.ZManaged]]-based constructors in `ModuleDef`:
    */
  implicit final def providerFromZManaged[R, E, A](
    managed: => ZManaged[R, E, A]
  )(implicit tag: Tag[Lifecycle.FromZIO[R, E, A]]
  ): Functoid[Lifecycle.FromZIO[R, E, A]] = {
    Functoid.lift(Lifecycle.fromZManaged(managed))
  }

  /**
    * Allows you to bind [[zio.managed.ZManaged]]-based constructors in `ModuleDef`:
    */
  // workaround for inference issues with `E=Nothing`, scalac error: Couldn't find Tag[FromZIO[Any, E, Clock]] when binding ZManaged[Any, Nothing, Clock]
  implicit final def providerFromZManagedNothing[R, A](
    managed: => ZManaged[R, Nothing, A]
  )(implicit tag: Tag[Lifecycle.FromZIO[R, Nothing, A]]
  ): Functoid[Lifecycle.FromZIO[R, Nothing, A]] = {
    Functoid.lift(Lifecycle.fromZManaged(managed))
  }

  /**
    * Allows you to bind [[zio.ZLayer]]-based constructors in `ModuleDef`:
    */
  implicit final def providerFromZLayer[R, E, A: Tag](
    layer: => ZLayer[R, E, A]
  )(implicit tag: Tag[Lifecycle.FromZIO[R, E, A]]
  ): Functoid[Lifecycle.FromZIO[R, E, A]] = {
    Functoid.lift(Lifecycle.fromZLayer(layer)(zio.Tag[A]))
  }

  /**
    * Allows you to bind [[zio.ZLayer]]-based constructors in `ModuleDef`:
    */
  // workaround for inference issues with `E=Nothing`, scalac error: Couldn't find Tag[FromZIO[Any, E, Clock]] when binding ZManaged[Any, Nothing, Clock]
  implicit final def providerFromZLayerNothing[R, A: Tag](
    layer: => ZLayer[R, Nothing, A]
  )(implicit tag: Tag[Lifecycle.FromZIO[R, Nothing, A]]
  ): Functoid[Lifecycle.FromZIO[R, Nothing, A]] = {
    Functoid.lift(Lifecycle.fromZLayer(layer)(zio.Tag[A]))
  }

  disableAutoTrace.discard()
}
