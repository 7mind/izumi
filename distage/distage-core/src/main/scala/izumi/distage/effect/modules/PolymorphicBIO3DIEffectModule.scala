package izumi.distage.effect.modules

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.effect._
import izumi.functional.bio.{BIO, BIOApplicative, BIOAsync, BIOAsync3, BIOFork, BIOFork3, BIOPrimitives, BIOPrimitives3, BIORunner, BIORunner3, BIOTemporal, BIOTemporal3, SyncSafe2, SyncSafe3}
import izumi.functional.mono.SyncSafe
import izumi.fundamentals.platform.language.unused
import izumi.reflect.TagK3

/** Any `BIO` effect type support for `distage` resources, effects, roles & tests.
  *
  * For any `F[-_, +_, +_]` with available `make[BIOAsync3[F]]`, `make[BIOTemporal3[F]]` and `make[BIORunner3[F]]` bindings.
  *
  * - Adds [[izumi.distage.model.effect.DIEffect]] instances to support using `F[-_, +_, +_]` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  * - Adds [[izumi.functional.bio]] typeclass instances for `F[-_, +_, +_]`
  *
  * Depends on `make[BIOAsync3[F]]`, `make[BIOTemporal3[F]]`, `make[BIOFork3[F]]` & `make[BIORunner3[F]]`
  */
class PolymorphicBIO3DIEffectModule[F[-_, +_, +_]: TagK3] extends ModuleDef {
  include(PolymorphicBIOTypeclassesModule[F[Any, +?, +?]])

  make[BIOAsync[F[Any, +?, +?]]].from {
    implicit F: BIOAsync3[F] => BIOAsync[F[Any, +?, +?]]
  }
  make[BIOTemporal[F[Any, +?, +?]]].from {
    implicit F: BIOTemporal3[F] => BIOTemporal[F[Any, +?, +?]]
  }
  make[BIOFork[F[Any, +?, +?]]].from {
    implicit ForK: BIOFork3[F] => BIOFork[F[Any, +?, +?]]
  }

  make[DIEffectRunner3[F]]
    .from[DIEffectRunner.BIOImpl[F[Any, +?, +?]]]

  make[DIEffect3[F]].from {
    DIEffect.fromBIO(_: BIO[F[Any, +?, +?]])
  }
  make[DIApplicative3[F]].from {
    DIApplicative.fromBIO[F[Any, +?, +?], Throwable](_: BIOApplicative[F[Any, +?, +?]])
  }
  make[DIEffectAsync3[F]].from {
    DIEffectAsync.fromBIOTemporal(_: BIOAsync[F[Any, +?, +?]], _: BIOTemporal[F[Any, +?, +?]])
  }
  make[SyncSafe3[F]].from {
    SyncSafe.fromBIO(_: BIO[F[Any, +?, +?]])
  }
}

object PolymorphicBIO3DIEffectModule extends ModuleDef {
  @inline def apply[F[-_, +_, +_]: TagK3]: PolymorphicBIO3DIEffectModule[F] = new PolymorphicBIO3DIEffectModule

  /**
    * Make [[PolymorphicBIO3DIEffectModule]], binding the required dependencies in place to values from implicit scope
    *
    * `make[BIOFork3[F]]` and `make[BIOPrimitives3[F]]` are not required by [[PolymorphicBIO3DIEffectModule]]
    * but are added for completeness
    */
  def withImplicits[F[-_, +_, +_]: TagK3: BIOAsync3: BIOTemporal3: BIORunner3: BIOFork3: BIOPrimitives3]: ModuleDef = new ModuleDef {
    include(PolymorphicBIO3DIEffectModule[F])

    addImplicit[BIOAsync3[F]]
    addImplicit[BIOTemporal3[F]]
    addImplicit[BIOFork3[F]]
    addImplicit[BIOPrimitives3[F]]
    addImplicit[BIORunner3[F]]

    // no corresponding bifunctor (`F[Any, +?, +?]`) instances need to be added for these types because they already match
    @unused private[this] def aliasingCheck(): Unit = {
      implicitly[BIORunner3[F] =:= BIORunner[F[Any, +?, +?]]]
      implicitly[BIOPrimitives3[F] =:= BIOPrimitives[F[Any, +?, +?]]]
      implicitly[SyncSafe3[F] =:= SyncSafe2[F[Any, +?, +?]]]
      implicitly[DIEffectRunner3[F] =:= DIEffectRunner2[F[Any, +?, +?]]]
      implicitly[DIEffect3[F] =:= DIEffect2[F[Any, +?, +?]]]
      implicitly[DIApplicative3[F] =:= DIApplicative2[F[Any, +?, +?]]]
      implicitly[DIEffectAsync3[F] =:= DIEffectAsync2[F[Any, +?, +?]]]
      ()
    }
  }
}
