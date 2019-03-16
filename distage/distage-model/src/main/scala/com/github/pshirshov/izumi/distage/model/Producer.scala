package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.definition.DIResource.DIResourceBase
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.plan.OrderedPlan
import com.github.pshirshov.izumi.distage.model.provisioning.FailedProvision
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity

trait Producer {
  def produceF[F[_]: TagK: DIEffect](plan: OrderedPlan): DIResourceBase[F, Locator] { type InnerResource <: Either[FailedProvision[F], Locator] }
  def produceUnsafeF[F[_]: TagK](plan: OrderedPlan)(implicit F: DIEffect[F]): F[Locator] = {
    // FIXME: remove
    F.flatMap(produceF[F](plan).allocate)(x => F.maybeSuspend(x.throwOnFailure()))
  }

  // FIXME: nonmonadic ???
  final def produce(plan: OrderedPlan): DIResourceBase[Identity, Locator] { type InnerResource <: Either[FailedProvision[Identity], Locator] } = {
    produceF[Identity](plan)
  }
  final def produceUnsafe(plan: OrderedPlan): Locator = produce(plan).allocate.throwOnFailure()
}
