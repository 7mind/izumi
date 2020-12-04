package izumi.distage.modules.support

import cats.Parallel
import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Timer}
import izumi.distage.model.definition.{Id, ModuleDef}
import izumi.distage.modules.platform.MonixBIOPlatformDependentSupportModule
import izumi.distage.modules.typeclass.BIO2InstancesModule
import izumi.functional.bio.{Async2, Fork2, Primitives2, Temporal2}
import monix.bio.{IO, Task, UIO}
import monix.execution.Scheduler

object MonixBIOSupportModule extends MonixBIOSupportModule

/**
  * `monix.bio.IO` effect type support for `distage` resources, effects, roles & tests
  *
  *  - Adds [[izumi.distage.model.effect.QuasiIO]] instances to support using `monix-bio` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  *  - Adds [[izumi.functional.bio]] typeclass instances for `monix-bio`
  *  - Adds `cats-effect` typeclass instances for `monix-bio`
  *
  * @note Will also add the following components:
  *   - [[monix.execution.Scheduler Scheduler]] by using [[monix.execution.Scheduler.global]]
  *   - [[monix.execution.Scheduler Scheduler @Id("io")]] by using [[monix.execution.Scheduler.io]]
  *   - [[monix.bio.IO.Options]] by using [[monix.bio.IO.defaultOptions]]
  *   - [[cats.effect.Blocker]] by using `Scheduler @Id("io")`
  *
  * Bindings to the same keys in your own [[izumi.distage.model.definition.ModuleDef]] or plugins will override these defaults.
  */
trait MonixBIOSupportModule extends ModuleDef with MonixBIOPlatformDependentSupportModule {
  // QuasiIO & cats-effect instances
  include(AnyCatsEffectSupportModule[Task])
  // BIO instances
  include(BIO2InstancesModule[IO])

  make[Scheduler].from(Scheduler.global)
  make[IO.Options].from(IO.defaultOptions)
  make[Scheduler].named("io").from(Scheduler.io())
  make[Blocker].from(Blocker.liftExecutionContext(_: Scheduler @Id("io")))

  addImplicit[Async2[IO]]
  make[Temporal2[IO]].from {
    implicit T: Timer[UIO] => implicitly[Temporal2[IO]]
  }
  addImplicit[Fork2[IO]]
  addImplicit[Primitives2[IO]]

  make[ConcurrentEffect[Task]].from(IO.catsEffect(_: Scheduler, _: IO.Options))
  addImplicit[Parallel[Task]]

  addImplicit[ContextShift[Task]]
  addImplicit[Timer[Task]]
  addImplicit[Timer[UIO]]
}
