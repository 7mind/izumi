package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.definition.DIResource.DIResourceBase
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect.syntax._
import com.github.pshirshov.izumi.distage.model.plan.OrderedPlan
import com.github.pshirshov.izumi.distage.model.provisioning.FailedProvision
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity

trait Producer {
  def produceF[F[_]: TagK: DIEffect](plan: OrderedPlan): DIResourceBase[F, Locator]
  def produceDetailedF[F[_]: TagK: DIEffect](plan: OrderedPlan): DIResourceBase[F, Either[FailedProvision[F], Locator]]

  final def produce(plan: OrderedPlan): DIResourceBase[Identity, Locator] = produceF[Identity](plan)
  final def produceDetailed(plan: OrderedPlan): DIResourceBase[Identity, Either[FailedProvision[Identity], Locator]] = produceDetailedF[Identity](plan)

  final def produceUnsafeF[F[_]: TagK: DIEffect](plan: OrderedPlan): F[Locator] = {
    val resource = produceF[F](plan)
    resource.allocate.map(resource.extract)
  }
  final def produceUnsafe(plan: OrderedPlan): Locator = produceUnsafeF[Identity](plan)
}
