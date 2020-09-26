package izumi.distage.modules.support

import cats.Parallel
import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.platform.{MonixBIOPlatformSupportModule, MonixPlatformSupportModule}
import izumi.distage.modules.typeclass.BIOInstancesModule
import izumi.functional.bio.{BIOAsync, BIOFork, BIOPrimitives, BIOTemporal}
import monix.bio.{IO, Task, UIO}
import monix.execution.Scheduler

object MonixBIOSupportModule extends MonixBIOSupportModule

/** `monix.bio.IO` effect type support for `distage` resources, effects, roles & tests
  *
  * - Adds [[izumi.distage.model.effect.DIEffect]] instances to support using `monix-bio` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  * - Adds [[izumi.functional.bio]] typeclass instances for `monix-bio`
  * - Adds `cats-effect` typeclass instances for `monix-bio`
  *
  * Note: by default this module will implement
  *   - [[monix.execution.Scheduler Scheduler]] using [[monix.execution.Scheduler.global]]
  *   - `Scheduler @Id("io")` using [[monix.execution.Scheduler.io]]
  *   - [[monix.bio.IO.Options]] using [[monix.bio.IO.defaultOptions]]
  *
  * Bindings to the same keys in your own [[izumi.distage.model.definition.ModuleDef]] or plugins will override these defaults.
  */
trait MonixBIOSupportModule extends ModuleDef {
  // DIEffect & cats-effect instances
  include(AnyCatsEffectSupportModule[Task])
  // BIO instances
  include(BIOInstancesModule[IO])
  include(MonixPlatformSupportModule)
  include(MonixBIOPlatformSupportModule)

  make[Scheduler].from(Scheduler.global)
  make[IO.Options].from(IO.defaultOptions)

  addImplicit[BIOAsync[IO]]
  make[BIOTemporal[IO]].from {
    implicit T: Timer[UIO] => implicitly[BIOTemporal[IO]]
  }
  addImplicit[BIOFork[IO]]
  addImplicit[BIOPrimitives[IO]]

  make[ConcurrentEffect[Task]].from(IO.catsEffect(_: Scheduler, _: IO.Options))
  addImplicit[Parallel[Task]]

  addImplicit[ContextShift[Task]]
  addImplicit[Timer[Task]]
  addImplicit[Timer[UIO]]
}
