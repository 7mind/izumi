package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.plan.OrderedPlan
import com.github.pshirshov.izumi.distage.model.provisioning.FailedProvision
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity

trait Producer {
  def produceF[F[_]: TagK: DIEffect](plan: OrderedPlan): F[Either[FailedProvision[F], Locator]]

  // FIXME: nonmonadic ???
  final def produce(plan: OrderedPlan): Either[FailedProvision[Identity], Locator] = produceF[Identity](plan)
  final def produceUnsafe(plan: OrderedPlan): Locator = produce(plan).throwOnFailure()
}
