package izumi.distage.effect.modules

import cats.Parallel
import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import izumi.distage.model.definition.ModuleDef
import monix.eval.Task
import monix.execution.Scheduler

/** `monix.eval.Task` effect type support for `distage` resources, effects, roles & tests
  *
  * - Adds [[izumi.distage.model.effect.DIEffect]] instances to support using `monix` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  * - Adds `cats-effect` typeclass instances for `monix`
  *
  * @param s is a [[monix.execution.Scheduler Scheduler]] that needs to be available in scope
  */
class MonixDIEffectModule(
  implicit s: Scheduler = Scheduler.global,
  opts: Task.Options = Task.defaultOptions,
) extends ModuleDef {
  // DIEffect & cats-effect instances
  include(PolymorphicCatsDIEffectModule[Task])

  make[ConcurrentEffect[Task]].from(Task.catsEffect)
  addImplicit[Parallel[Task]]

  addImplicit[ContextShift[Task]]
  addImplicit[Timer[Task]]
}

object MonixDIEffectModule {
  /** @param s is a [[monix.execution.Scheduler Scheduler]] that needs to be available in scope */
  @inline def apply(
    implicit s: Scheduler = Scheduler.global,
    opts: Task.Options = Task.defaultOptions,
  ): MonixDIEffectModule = new MonixDIEffectModule
}
