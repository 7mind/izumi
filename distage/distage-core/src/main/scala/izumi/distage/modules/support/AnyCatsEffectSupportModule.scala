package izumi.distage.modules.support

import cats.Parallel
import cats.effect.kernel.{Async, GenTemporal, Sync}
import cats.effect.std.Dispatcher
import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.typeclass.CatsEffectInstancesModule
import izumi.functional.bio.{Clock1, Entropy1, SyncSafe1}
import izumi.functional.bio.*
import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.TagK

object AnyCatsEffectSupportModule {
  /**
    * Any `cats-effect` effect type support for `distage` resources, effects, roles & tests.
    *
    * For all `F[_]` with available `make[Async[F]]`, `make[Parallel[F]]` and `make[Dispatcher[F]]` bindings.
    *
    *  - Adds [[izumi.functional.bio.IO1]] instances to support using `F[_]` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
    *  - Adds `cats-effect` typeclass instances for `F[_]`
    *
    * Depends on `make[Async[F]]`, `make[Parallel[F]]`, `make[Dispatcher[F]]`.
    */
  def usingAsyncParallelDispatcher[F[_]: TagK]: ModuleDef = new ModuleDef {
    include(AnyCatsEffectSupportModule.usingAsyncParallel[F])

    make[IORunner1[F]].from {
      (dispatcher: Dispatcher[F]) =>
        IORunner1.mkFromCatsDispatcher(dispatcher)
    }
  }

  def usingAsyncParallel[F[_]: TagK]: ModuleDef = new ModuleDef {
    include(CatsEffectInstancesModule.usingAsync[F])

    addImplicit[TagK[F]]

    make[IO1[F]]
      .aliased[Primitives1[F]]
      .aliased[Applicative1[F]]
      .aliased[Functor1[F]]
      .from {
        implicit F: Sync[F] => IO1.fromCats[F, Sync]
      }
    make[Async1[F]].from {
      implicit F: Async[F] => Async1.fromCats[F, Async]
    }
    make[Temporal1[F]].from {
      implicit F: GenTemporal[F, Throwable] => Temporal1.fromCats[F, GenTemporal]
    }
    make[SyncSafe1[F]].from {
      implicit F: Sync[F] => SyncSafe1.fromSync[F, Sync]
    }
    make[Clock1[F]].from {
      Clock1.fromImpure(_: Clock1[Identity])(using _: SyncSafe1[F])
    }
    make[Entropy1[F]].from {
      Entropy1.fromImpure(_: Entropy1[Identity])(using _: SyncSafe1[F])
    }
  }

  /**
    * Make [[AnyCatsEffectSupportModule.usingAsyncParallelDispatcher]], binding the required dependencies in place to values from implicit scope
    */
  def withImplicits[F[_]: TagK: Async: Parallel: Dispatcher]: ModuleDef = new ModuleDef {
    include(AnyCatsEffectSupportModule.usingAsyncParallelDispatcher[F])

    addImplicit[Async[F]]
    addImplicit[Parallel[F]]
    addImplicit[Dispatcher[F]]
  }

  @deprecated("renamed to usingDependencies", "1.3")
  @inline def apply[F[_]: TagK]: ModuleDef = usingAsyncParallel[F]
}
