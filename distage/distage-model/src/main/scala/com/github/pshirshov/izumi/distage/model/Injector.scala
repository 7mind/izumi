package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.definition.DIResource.DIResourceBase
import com.github.pshirshov.izumi.distage.model.definition.ModuleBase
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK

trait Injector extends Planner with Producer {

  final def produceF[F[_]: TagK: DIEffect](input: PlannerInput): DIResourceBase[F, Locator] = {
    produceF[F](plan(input))
  }
  final def produceF[F[_]: TagK: DIEffect](input: ModuleBase, roots: Set[DIKey] = Set.empty): DIResourceBase[F, Locator] = {
    produceF[F](plan(PlannerInput(input, roots)))
  }

  final def produceUnsafe(input: PlannerInput): Locator = {
    produceUnsafe(plan(input))
  }
  final def produceUnsafe(input: ModuleBase, roots: Set[DIKey] = Set.empty): Locator = {
    produceUnsafe(plan(PlannerInput(input, roots)))
  }

}
