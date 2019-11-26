package izumi.distage.model

import izumi.distage.model.definition.DIResource.DIResourceBase
import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.monadic.DIEffect
import izumi.distage.model.plan.GCMode
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import izumi.fundamentals.platform.functional.Identity

/**
  * Injector can create an object graph ([[Locator]]) from [[ModuleBase]] or [[izumi.distage.model.plan.OrderedPlan]]
  *
  * @see [[Planner]]
  * @see [[Producer]]
  * */
trait Injector extends Planner with Producer {

  /**
    * Create an effectful [[DIResourceBase]] value that encapsulates the
    * allocation and cleanup of an object graph described by `input`
    *
    * @param input Bindings created by [[izumi.distage.model.definition.ModuleDef]] DSL
    *              and garbage collection roots.
    *
    *              Garbage collector will remove all bindings that aren't direct or indirect dependencies
    *              of the chosen root DIKeys from the plan - they will never be instantiated.
    *
    *              If left empty, garbage collection will not be performed – that would be equivalent to
    *              designating all DIKeys as roots.
    * @return A Resource value that encapsulates allocation and cleanup of the object graph described by `input`
    */
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
