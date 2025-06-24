package izumi.distage.modules.support

import cats.Parallel
import cats.effect.kernel.{Async, GenTemporal, Sync}
import cats.effect.std.Dispatcher
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.providers.Functoid
import izumi.distage.modules.typeclass.CatsEffectInstancesModule
import izumi.functional.bio.{Clock1, Entropy1, SyncSafe1}
import izumi.functional.quasi.*
import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.TagK

/**
  * Any `cats-effect` effect type support for `distage` resources, effects, roles & tests.
  *
  * For all `F[_]` with available `make[ConcurrentEffect[F]]`, `make[Parallel[F]]` and `make[Timer[F]]` bindings.
  *
  *  - Adds [[izumi.functional.quasi.QuasiIO]] instances to support using `F[_]` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  *  - Adds `cats-effect` typeclass instances for `F[_]`
  *
  * Depends on `make[ConcurrentEffect[F]]`, `make[Parallel[F]]`, `make[Timer[F]]`.
  */
class AnyCatsEffectSupportModule[F[_]: TagK] extends ModuleDef {
  include(CatsEffectInstancesModule[F])

  addImplicit[TagK[F]]

  make[QuasiIO[F]]
    .aliased[QuasiPrimitives[F]]
    .aliased[QuasiApplicative[F]]
    .aliased[QuasiFunctor[F]]
    .fromNoCapture {
      implicit F: Sync[F] => QuasiIO.fromCats[F, Sync]
    }
  make[QuasiAsync[F]].fromNoCapture {
      implicit F: Async[F] => QuasiAsync.fromCats[F, Async]
  }
  make[QuasiTemporal[F]].fromNoCapture {
    implicit F: GenTemporal[F, Throwable] => QuasiTemporal.fromCats[F, GenTemporal]
  }
  make[SyncSafe1[F]].fromNoCapture {
    implicit F: Sync[F] => SyncSafe1.fromSync[F, Sync]
  }
  make[Clock1[F]].from {
    Clock1.fromImpure(_: Clock1[Identity])(using _: SyncSafe1[F])
  }
  make[Entropy1[F]].from {
    Entropy1.fromImpure(_: Entropy1[Identity])(using _: SyncSafe1[F])
  }

  /*
  (
  (
  evidence$1: izumi.fundamentals.orphans.cats.effect.kernel.Sync[[F >: scala.Nothing <: [_$1 >: scala.Nothing <: scala.Any] => scala.Any] => cats.effect.kernel.Sync[F]],
  F: cats.effect.kernel.Sync[AnyCatsEffectSupportModule.this.F]
  ) => (
  (implicit `F₂`: cats.effect.kernel.Sync[AnyCatsEffectSupportModule.this.F]) =>
  izumi.functional.quasi.QuasiIO.fromCats[AnyCatsEffectSupportModule.this.F, [F >: scala.Nothing <: [_$1 >: scala.Nothing <: scala.Any] => scala.Any] =>
  cats.effect.kernel.Sync[F]](evidence$1, `F₂`)
  ).apply(F))
   */

  /*
  (
  (implicit F: cats.effect.kernel.Sync[AnyCatsEffectSupportModule.this.F]) =>
  izumi.functional.quasi.QuasiIO.fromCats[AnyCatsEffectSupportModule.this.F, [F >: scala.Nothing <: [_$1 >: scala.Nothing <: scala.Any] => scala.Any] =>
  cats.effect.kernel.Sync[F]](evidence$1, F))
   */

  /*
  (
  (implicit F: cats.effect.kernel.Sync[AnyCatsEffectSupportModule.this.F]) =>
  izumi.functional.quasi.QuasiIO.fromCats[AnyCatsEffectSupportModule.this.F, [F >: scala.Nothing <: [_$1 >: scala.Nothing <: scala.Any] => scala.Any] =>
  cats.effect.kernel.Sync[F]](izumi.fundamentals.orphans.cats.effect.kernel.Sync.get, F))

   */
}

object AnyCatsEffectSupportModule {
  @inline def apply[F[_]: TagK]: AnyCatsEffectSupportModule[F] = new AnyCatsEffectSupportModule[F]

  /**
    * Make [[AnyCatsEffectSupportModule]], binding the required dependencies in place to values from implicit scope
    *
    * `make[ContextShift[F]]` is not required by [[AnyCatsEffectSupportModule]] but is added for completeness
    */
  def withImplicits[F[_]: TagK: Async: Parallel: Dispatcher]: ModuleDef = new ModuleDef {
    addImplicit[Async[F]]
    addImplicit[Parallel[F]]
    addImplicit[Dispatcher[F]]

    make[QuasiIORunner[F]].from {
      (dispatcher: Dispatcher[F]) =>
        QuasiIORunner.mkFromCatsDispatcher(dispatcher)
    }

    include(AnyCatsEffectSupportModule[F])
  }
}
