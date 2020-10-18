package izumi.distage.modules.support

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.effect._
import izumi.distage.modules.typeclass.BIOInstancesModule
import izumi.functional.bio.{Applicative2, Async2, Fork2, IO2, Primitives2, SyncSafe2, Temporal2, UnsafeRun2}
import izumi.functional.mono.SyncSafe
import izumi.reflect.TagKK

/** Any `BIO` effect type support for `distage` resources, effects, roles & tests.
  *
  * For any `F[+_, +_]` with available `make[Async2[F]]`, `make[Temporal2[F]]` and `make[UnsafeRun2[F]]` bindings.
  *
  * - Adds [[izumi.distage.model.effect.QuasiIO]] instances to support using `F[+_, +_]` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  * - Adds [[izumi.functional.bio]] typeclass instances for `F[+_, +_]`
  *
  * Depends on `make[Async2[F]]`, `make[Temporal2[F]]`, `make[UnsafeRun2[F]]`
  */
class AnyBIOSupportModule[F[+_, +_]: TagKK] extends ModuleDef {
  include(BIOInstancesModule[F])

  make[QuasiIORunner2[F]]
    .from[QuasiIORunner.BIOImpl[F]]

  make[QuasiIO2[F]].from {
    QuasiIO.fromBIO(_: IO2[F])
  }
  make[QuasiApplicative2[F]].from {
    QuasiApplicative.fromBIO[F, Throwable](_: Applicative2[F])
  }
  make[QuasiAsync2[F]].from {
    QuasiAsync.fromBIO(_: Async2[F], _: Temporal2[F])
  }
  make[SyncSafe2[F]].from {
    SyncSafe.fromBIO(_: IO2[F])
  }
}

object AnyBIOSupportModule extends ModuleDef {
  @inline def apply[F[+_, +_]: TagKK]: AnyBIOSupportModule[F] = new AnyBIOSupportModule

  /**
    * Make [[AnyBIOSupportModule]], binding the required dependencies in place to values from implicit scope
    *
    * `make[Fork2[F]]` and `make[Primitives2[F]]` are not required by [[AnyBIOSupportModule]]
    * but are added for completeness
    */
  def withImplicits[F[+_, +_]: TagKK: Async2: Temporal2: UnsafeRun2: Fork2: Primitives2]: ModuleDef = new ModuleDef {
    include(AnyBIOSupportModule[F])

    addImplicit[Async2[F]]
    addImplicit[Fork2[F]]
    addImplicit[Temporal2[F]]
    addImplicit[Primitives2[F]]
    addImplicit[UnsafeRun2[F]]
  }
}
