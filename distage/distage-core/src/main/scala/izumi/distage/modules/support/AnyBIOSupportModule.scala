package izumi.distage.modules.support

import izumi.distage.model.definition.ModuleDef
import izumi.functional.quasi.*
import izumi.distage.modules.typeclass.BIOInstancesModule
import izumi.functional.bio.retry.Scheduler2
import izumi.functional.bio.{Async2, Clock1, Clock2, Entropy1, Entropy2, Fork2, IO2, Primitives2, PrimitivesLocal2, PrimitivesM2, SyncSafe1, SyncSafe2, Temporal2, UnsafeRun2}
import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.{TagK, TagKK}

import scala.concurrent.ExecutionContext

/**
  * Any `BIO` effect type support for `distage` resources, effects, roles & tests.
  *
  * For all `F[+_, +_]` with available `make[Async2[F]]`, `make[Temporal2[F]]` and `make[UnsafeRun2[F]]` bindings.
  *
  *  - Adds [[izumi.functional.quasi.QuasiIO]] instances to support using `F[+_, +_]` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  *  - Adds [[izumi.functional.bio]] typeclass instances for `F[+_, +_]`
  *
  * Depends on `make[Async2[F]]`, `make[Temporal2[F]]`, `make[UnsafeRun2[F]]`, `make[Fork2[F]]`
  * Optional additions: `make[Primitives2[F]]`, `make[PrimitivesM2[F]]`, `make[Scheduler2[F]]`
  */
class AnyBIOSupportModule[F[+_, +_]: TagKK](implicit t: TagK[F[Throwable, _]], tn: TagK[F[Nothing, _]]) extends ModuleDef {
  include(BIOInstancesModule[F])

  make[TagK[F[Nothing, _]]].fromValue(tn)
  make[TagK[F[Throwable, _]]].fromValue(t)
  addImplicit[TagKK[F]]

  make[QuasiIORunner2[F]]
    .from[QuasiIORunner.BIOImpl[F]]
    .annotateParameter[ExecutionContext]("cpu") // scala.js

  make[QuasiIO2[F]]
    .aliased[QuasiPrimitives2[F]]
    .aliased[QuasiApplicative2[F]]
    .aliased[QuasiFunctor2[F]]
    .from {
      QuasiIO.fromBIO(_: IO2[F])
    }
  make[QuasiAsync2[F]].from {
    QuasiAsync.fromBIO(_: Async2[F])
  }
  make[QuasiTemporal2[F]].from {
    QuasiTemporal.fromBIO(_: Temporal2[F])
  }
  make[SyncSafe2[F]].from {
    SyncSafe1.fromBIO(_: IO2[F])
  }
  make[SyncSafe1[F[Throwable, _]]].from((_: SyncSafe2[F]).widen[F[Throwable, _]])
  make[Clock2[F]].from {
    Clock1.fromImpure(_: Clock1[Identity])(_: SyncSafe2[F])
  }
  make[Entropy2[F]].from {
    Entropy1.fromImpure(_: Entropy1[Identity])(_: SyncSafe2[F])
  }
  make[Clock1[F[Throwable, _]]].from {
    Clock1.covarianceConversion[F[Nothing, _], F[Throwable, _]](_: Clock2[F])
  }
  make[Entropy1[F[Throwable, _]]].from {
    Entropy1.covarianceConversion[F[Nothing, _], F[Throwable, _]](_: Entropy2[F])
  }
}

object AnyBIOSupportModule extends ModuleDef {
  @inline def apply[F[+_, +_]: TagKK](implicit t: TagK[F[Throwable, _]], tn: TagK[F[Nothing, _]]): AnyBIOSupportModule[F] = new AnyBIOSupportModule

  /**
    * Make [[AnyBIOSupportModule]], binding the required dependencies in place to values from implicit scope
    *
    * `make[Fork2[F]]` and `make[Primitives2[F]]` are not required by [[AnyBIOSupportModule]]
    * but are added for completeness
    */
  def withImplicits[F[+_, +_]: TagKK: Async2: Temporal2: UnsafeRun2: Fork2: Primitives2: PrimitivesM2: PrimitivesLocal2: Scheduler2](
    implicit t: TagK[F[Throwable, _]],
    tn: TagK[F[Nothing, _]],
  ): ModuleDef = new ModuleDef {
    include(AnyBIOSupportModule[F])

    addImplicit[Async2[F]]
    addImplicit[Fork2[F]]
    addImplicit[Temporal2[F]]
    addImplicit[Primitives2[F]]
    addImplicit[PrimitivesM2[F]]
    addImplicit[PrimitivesLocal2[F]]
    addImplicit[UnsafeRun2[F]]
    addImplicit[Scheduler2[F]]
  }
}
