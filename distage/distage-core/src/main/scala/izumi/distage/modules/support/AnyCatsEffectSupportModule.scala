package izumi.distage.modules.support

import cats.Parallel
import cats.effect.kernel.{Async, GenTemporal, Sync}
import cats.effect.std.Dispatcher
import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.typeclass.CatsEffectInstancesModule
import izumi.functional.bio.{Clock1, Entropy1, SyncSafe1}
import izumi.functional.quasi.*
import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.TagK

object AnyCatsEffectSupportModule {
  /**
    * Any `cats-effect` effect type support for `distage` resources, effects, roles & tests.
    *
    * For all `F[_]` with available `make[Async[F]]`, `make[Parallel[F]]` and `make[Dispatcher[F]]` bindings.
    *
    *  - Adds [[izumi.functional.quasi.QuasiIO]] instances to support using `F[_]` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
    *  - Adds `cats-effect` typeclass instances for `F[_]`
    *
    * Depends on `make[Async[F]]`, `make[Parallel[F]]`, `make[Dispatcher[F]]`.
    */
  def usingAsyncParallelDispatcher[F[_]: TagK]: ModuleDef = new ModuleDef {
    include(AnyCatsEffectSupportModule.usingAsyncParallel[F])

    make[QuasiIORunner[F]].from {
      (dispatcher: Dispatcher[F]) =>
        QuasiIORunner.mkFromCatsDispatcher(dispatcher)
    }
  }

  def usingAsyncParallel[F[_]: TagK]: ModuleDef = new ModuleDef {
    include(CatsEffectInstancesModule.usingAsync[F])

    addImplicit[TagK[F]]

    make[QuasiIO[F]]
      .aliased[QuasiPrimitives[F]]
      .aliased[QuasiApplicative[F]]
      .aliased[QuasiFunctor[F]]
      .from {
        implicit F: Sync[F] => QuasiIO.fromCats[F, Sync]
      }
    make[QuasiAsync[F]].from {
      implicit F: Async[F] => QuasiAsync.fromCats[F, Async]
    }
    make[QuasiTemporal[F]].from {
      implicit F: GenTemporal[F, Throwable] => QuasiTemporal.fromCats[F, GenTemporal]
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
