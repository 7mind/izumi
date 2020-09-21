package izumi.distage.effect.modules

import cats.Parallel
import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import izumi.distage.model.definition.ModuleDef
import monix.bio.{IO, Task}
import monix.execution.Scheduler

/** `monix.bio.IO` effect type support for `distage` resources, effects, roles & tests
  *
  * - Adds [[izumi.functional.bio]] typeclass instances for `monix-bio`
  * - Adds [[cats.effect]] typeclass instances for `monix-bio`
  * - Adds [[izumi.distage.model.effect.DIEffect]] instances to support using `monix-bio` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  *
  * @param s is a [[monix.execution.Scheduler Scheduler]] that needs to be available in scope
  */
class MonixBIODIEffectModule(
  implicit s: Scheduler = Scheduler.global,
  opts: IO.Options = IO.defaultOptions,
) extends ModuleDef {
  // DIEffect & Cats typeclasses
  include(PolymorphicCatsDIEffectModule[Task])
  // BIO typeclasses
  include(PolymorphicBIOTypeclassesModule[IO])

  make[ConcurrentEffect[Task]].from(IO.catsEffect)

  addImplicit[ContextShift[Task]]
  addImplicit[Parallel[Task]]
  addImplicit[Timer[Task]]
}

object MonixBIODIEffectModule {
  /** @param s is a [[monix.execution.Scheduler Scheduler]] that needs to be available in scope */
  def apply(
    implicit s: Scheduler = Scheduler.global,
    opts: IO.Options = IO.defaultOptions,
  ): MonixBIODIEffectModule = new MonixBIODIEffectModule
}
