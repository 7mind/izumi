package izumi.distage.modules.support

import cats.Parallel
import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.platform.MonixPlatformDependentSupportModule
import monix.eval.Task
import monix.execution.Scheduler

object MonixSupportModule extends MonixSupportModule

/** `monix.eval.Task` effect type support for `distage` resources, effects, roles & tests
  *
  * - Adds [[izumi.distage.model.effect.QuasiEffect]] instances to support using `monix` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  * - Adds `cats-effect` typeclass instances for `monix`
  *
  * Note: by default this module will implement
  *   - [[monix.execution.Scheduler Scheduler]] using [[monix.execution.Scheduler.global]]
  *   - `Scheduler @Id("io")` using [[monix.execution.Scheduler.io]]
  *   - [[monix.eval.Task.Options]] using [[monix.eval.Task.defaultOptions]]
  *
  * Bindings to the same keys in your own [[izumi.distage.model.definition.ModuleDef]] or plugins will override these defaults.
  */
trait MonixSupportModule extends ModuleDef with MonixPlatformDependentSupportModule {
  // QuasiEffect & cats-effect instances
  include(AnyCatsEffectSupportModule[Task])

  make[Scheduler].from(Scheduler.global)
  make[Task.Options].from(Task.defaultOptions)

  make[ConcurrentEffect[Task]].from(Task.catsEffect(_: Scheduler, _: Task.Options))
  addImplicit[Parallel[Task]]

  addImplicit[ContextShift[Task]]
  addImplicit[Timer[Task]]
}
