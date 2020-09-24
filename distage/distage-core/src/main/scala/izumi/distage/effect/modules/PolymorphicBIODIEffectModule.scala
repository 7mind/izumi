package izumi.distage.effect.modules

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.effect._
import izumi.functional.bio.{BIO, BIOApplicative, BIOAsync, BIOTemporal}
import izumi.reflect.TagKK

/** Any `BIO` effect type support for `distage` resources, effects, roles & tests.
  *
  * For any `F[+_, +_]` with available `make[BIO[F]]`, `make[BIOApplicative[F]]`, `make[BIOAsync[F]]`, `make[BIOTemporal[F]]` and `make[BIORunner[F]]` bindings.
  *
  * - Adds [[izumi.distage.model.effect.DIEffect]] instances to support using `F[+_, +_]` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  * - Adds [[izumi.functional.bio]] typeclass instances for `F[+_, +_]`
  *
  * Depends on `make[BIO[F]]`, `make[BIOApplicative[F]]`, `make[BIOAsync[F]]`, `make[BIOTemporal[F]]`, `make[BIORunner[F]]`
  */
class PolymorphicBIODIEffectModule[F[+_, +_]: TagKK] extends ModuleDef {
  make[DIEffectRunner2[F]].from[DIEffectRunner.BIOImpl[F]]

  make[DIEffect2[F]].from {
    DIEffect.fromBIO(_: BIO[F])
  }
  make[DIApplicative2[F]].from {
    DIApplicative.fromBIO[F, Throwable](_: BIOApplicative[F])
  }
  make[DIEffectAsync2[F]].from {
    DIEffectAsync.fromBIOTemporal(_: BIOAsync[F], _: BIOTemporal[F])
  }
}
