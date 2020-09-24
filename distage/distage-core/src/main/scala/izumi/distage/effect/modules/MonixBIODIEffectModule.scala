package izumi.distage.effect.modules

import cats.Parallel
import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import izumi.distage.model.definition.ModuleDef
import monix.bio.{IO, Task}
import monix.execution.Scheduler

/** `monix.bio.IO` effect type support for `distage` resources, effects, roles & tests
  *
  * - Adds [[izumi.distage.model.effect.DIEffect]] instances to support using `monix-bio` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  * - Adds [[izumi.functional.bio]] typeclass instances for `monix-bio`
  * - Adds [[cats.effect]] typeclass instances for `monix-bio`
  *
  * @param s is a [[monix.execution.Scheduler Scheduler]] that needs to be available in scope - alternatively, you can override the defaults later in plugins or with `ModuleBase#overridenBy`
  */
class MonixBIODIEffectModule(
  implicit s: Scheduler = Scheduler.global,
  opts: IO.Options = IO.defaultOptions,
) extends ModuleDef {
  // DIEffect & cats-effect instances
  include(PolymorphicCatsDIEffectModule[Task])
  // BIO instances
  include(PolymorphicBIOTypeclassesModule[IO])

  make[Scheduler].fromValue(s)
  make[IO.Options].fromValue(opts)

  make[ConcurrentEffect[Task]].from(IO.catsEffect(_: Scheduler, _: IO.Options))
  addImplicit[Parallel[Task]]

  addImplicit[ContextShift[Task]]
  addImplicit[Timer[Task]]
}

object MonixBIODIEffectModule {
  /** @param s is a [[monix.execution.Scheduler Scheduler]] that needs to be available in scope - alternatively, you can override the defaults later in plugins or with `ModuleBase#overridenBy` */
  @inline def apply(
    implicit s: Scheduler = Scheduler.global,
    opts: IO.Options = IO.defaultOptions,
  ): MonixBIODIEffectModule = new MonixBIODIEffectModule
}
