package izumi.distage.modules.support

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.effect._
import izumi.distage.modules.typeclass.BIOInstancesModule
import izumi.functional.bio.{BIO, BIOApplicative, BIOAsync, BIOFork, BIOPrimitives, BIORunner, BIOTemporal, SyncSafe2}
import izumi.functional.mono.SyncSafe
import izumi.reflect.TagKK

/** Any `BIO` effect type support for `distage` resources, effects, roles & tests.
  *
  * For any `F[+_, +_]` with available `make[BIOAsync[F]]`, `make[BIOTemporal[F]]` and `make[BIORunner[F]]` bindings.
  *
  * - Adds [[izumi.distage.model.effect.QuasiEffect]] instances to support using `F[+_, +_]` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  * - Adds [[izumi.functional.bio]] typeclass instances for `F[+_, +_]`
  *
  * Depends on `make[BIOAsync[F]]`, `make[BIOTemporal[F]]`, `make[BIORunner[F]]`
  */
class AnyBIOSupportModule[F[+_, +_]: TagKK] extends ModuleDef {
  include(BIOInstancesModule[F])

  make[QuasiEffectRunner2[F]]
    .from[QuasiEffectRunner.BIOImpl[F]]

  make[QuasiEffect2[F]].from {
    QuasiEffect.fromBIO(_: BIO[F])
  }
  make[QuasiApplicative2[F]].from {
    QuasiApplicative.fromBIO[F, Throwable](_: BIOApplicative[F])
  }
  make[QuasiEffectAsync2[F]].from {
    QuasiAsync.fromBIOTemporal(_: BIOAsync[F], _: BIOTemporal[F])
  }
  make[SyncSafe2[F]].from {
    SyncSafe.fromBIO(_: BIO[F])
  }
}

object AnyBIOSupportModule extends ModuleDef {
  @inline def apply[F[+_, +_]: TagKK]: AnyBIOSupportModule[F] = new AnyBIOSupportModule

  /**
    * Make [[AnyBIOSupportModule]], binding the required dependencies in place to values from implicit scope
    *
    * `make[BIOFork[F]]` and `make[BIOPrimitives[F]]` are not required by [[AnyBIOSupportModule]]
    * but are added for completeness
    */
  def withImplicits[F[+_, +_]: TagKK: BIOAsync: BIOTemporal: BIORunner: BIOFork: BIOPrimitives]: ModuleDef = new ModuleDef {
    include(AnyBIOSupportModule[F])

    addImplicit[BIOAsync[F]]
    addImplicit[BIOFork[F]]
    addImplicit[BIOTemporal[F]]
    addImplicit[BIOPrimitives[F]]
    addImplicit[BIORunner[F]]
  }
}
