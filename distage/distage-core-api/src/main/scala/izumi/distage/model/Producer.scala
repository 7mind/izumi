package izumi.distage.model

import izumi.distage.model.definition.DIResource.DIResourceBase
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.plan.OrderedPlan
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FinalizerFilter}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.reflection.Tags.TagK

/** Executes instructions in [[OrderedPlan]] to produce a [[Locator]] */
trait Producer {
  private[distage] def produceFX[F[_]: TagK: DIEffect](plan: OrderedPlan, filter: FinalizerFilter[F]): DIResourceBase[F, Locator]
  private[distage] def produceDetailedFX[F[_]: TagK: DIEffect](plan: OrderedPlan, filter: FinalizerFilter[F]): DIResourceBase[F, Either[FailedProvision[F], Locator]]

  final def produceF[F[_]: TagK: DIEffect](plan: OrderedPlan): DIResourceBase[F, Locator] = {
    produceFX[F](plan, FinalizerFilter.all[F])
  }

  final def produceDetailedF[F[_]: TagK: DIEffect](plan: OrderedPlan): DIResourceBase[F, Either[FailedProvision[F], Locator]] = {
    produceDetailedFX[F](plan, FinalizerFilter.all[F])
  }

  final def produce(plan: OrderedPlan): DIResourceBase[Identity, Locator] = produceF[Identity](plan)
  final def produceDetailed(plan: OrderedPlan): DIResourceBase[Identity, Either[FailedProvision[Identity], Locator]] = produceDetailedF[Identity](plan)
}
