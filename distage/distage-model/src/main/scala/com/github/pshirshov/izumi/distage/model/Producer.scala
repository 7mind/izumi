package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.monadic.DIMonad
import com.github.pshirshov.izumi.distage.model.plan.OrderedPlan
import com.github.pshirshov.izumi.distage.model.provisioning.FailedProvision
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity

trait Producer {
  def produce[F[_]: TagK: DIMonad](plan: OrderedPlan): F[Either[FailedProvision[F], Locator]]

  // FIXME: nonmonadic ???
  final def produceUnsafe(plan: OrderedPlan): Locator = produce[Identity](plan).throwOnFailure()
}
