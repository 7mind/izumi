package izumi.distage.model

import izumi.distage.model.definition.DIResource.DIResourceBase
import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.monadic.DIEffect
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import izumi.fundamentals.platform.functional.Identity

trait Injector extends Planner with Producer {

  final def produceF[F[_]: TagK: DIEffect](input: PlannerInput): DIResourceBase[F, Locator] = {
    produceF[F](plan(input))
  }
  final def produceF[F[_]: TagK: DIEffect](input: ModuleBase, mode: GCMode): DIResourceBase[F, Locator] = {
    produceF[F](plan(PlannerInput(input, mode)))
  }

  final def produce(input: PlannerInput): DIResourceBase[Identity, Locator] = {
    produce(plan(input))
  }
  final def produce(input: ModuleBase, mode: GCMode): DIResourceBase[Identity, Locator] = {
    produce(plan(PlannerInput(input, mode)))
  }

  final def produceUnsafeF[F[_]: TagK: DIEffect](input: PlannerInput): F[Locator] = {
    produceUnsafeF[F](plan(input))
  }
  final def produceUnsafeF[F[_]: TagK: DIEffect](input: ModuleBase, mode: GCMode): F[Locator] = {
    produceUnsafeF[F](plan(PlannerInput(input, mode)))
  }

  final def produceUnsafe(input: PlannerInput): Locator = {
    produceUnsafe(plan(input))
  }
  final def produceUnsafe(input: ModuleBase, mode: GCMode): Locator = {
    produceUnsafe(plan(PlannerInput(input, mode)))
  }

}
