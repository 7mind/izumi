package izumi.distage.modules.support

import cats.effect.kernel.{Async, Sync}
import cats.{Applicative, Parallel}
import distage.{ModuleDef, TagK}
import izumi.distage.model.effect.{QuasiApplicative, QuasiIO}
import izumi.distage.modules.typeclass.CatsEffectInstancesModule
import izumi.functional.bio.{Clock1, Entropy1, SyncSafe1}
import izumi.fundamentals.platform.functional.Identity

/**
  * Any `cats-effect` effect type support for `distage` resources, effects, roles & tests.
  *
  * For all `F[_]` with available `make[ConcurrentEffect[F]]`, `make[Parallel[F]]` and `make[Timer[F]]` bindings.
  *
  *  - Adds [[izumi.distage.model.effect.QuasiIO]] instances to support using `F[_]` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  *  - Adds `cats-effect` typeclass instances for `F[_]`
  *
  * Depends on `make[ConcurrentEffect[F]]`, `make[Parallel[F]]`, `make[Timer[F]]`.
  */
class AnyCatsEffectSupportModule[F[_]: TagK] extends ModuleDef {
  include(CatsEffectInstancesModule[F])

//  make[QuasiIORunner[F]].from {
//    implicit F: Effect[F] => QuasiIORunner.fromCats
//  }
  make[QuasiIO[F]].from {
    implicit F: Sync[F] => QuasiIO.fromCats
  }
//  make[QuasiAsync[F]].from {
//    (C0: Concurrent[F], P0: Parallel[F]) =>
//      implicit val C: Concurrent[F] = C0
//      implicit val P: Parallel[F] = P0
//      QuasiAsync.fromCats
//  }
  make[QuasiApplicative[F]].from {
    implicit F: Applicative[F] => QuasiApplicative.fromCats
  }
  make[SyncSafe1[F]].from {
    implicit F: Sync[F] => SyncSafe1.fromSync
  }
  make[Clock1[F]].from {
    Clock1.fromImpure(_: Clock1[Identity])(_: SyncSafe1[F])
  }
  make[Entropy1[F]].from {
    Entropy1.fromImpure(_: Entropy1[Identity])(_: SyncSafe1[F])
  }
}

object AnyCatsEffectSupportModule {
  @inline def apply[F[_]: TagK]: AnyCatsEffectSupportModule[F] = new AnyCatsEffectSupportModule[F]

  /**
    * Make [[AnyCatsEffectSupportModule]], binding the required dependencies in place to values from implicit scope
    *
    * `make[ContextShift[F]]` is not required by [[AnyCatsEffectSupportModule]] but is added for completeness
    */
  def withImplicits[F[_]: TagK: Async: Parallel]: ModuleDef = new ModuleDef {
    addImplicit[Async[F]]
    addImplicit[Parallel[F]]

    include(AnyCatsEffectSupportModule[F])
  }
}
