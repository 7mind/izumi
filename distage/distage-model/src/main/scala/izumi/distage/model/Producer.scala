package izumi.distage.model

import izumi.distage.model.definition.DIResource.DIResourceBase
import izumi.distage.model.monadic.DIEffect
import izumi.distage.model.monadic.DIEffect.syntax._
import izumi.distage.model.plan.OrderedPlan
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FinalizersFilter}
import izumi.fundamentals.reflection.Tags.TagK
import izumi.fundamentals.platform.functional.Identity

/** Executes instructions in [[OrderedPlan]] to produce a [[Locator]] */
trait Producer {
  protected[distage] def produceFX[F[_]: TagK: DIEffect](plan: OrderedPlan, filter: FinalizersFilter[F]): DIResourceBase[F, Locator]
  protected[distage] def produceDetailedFX[F[_]: TagK: DIEffect](plan: OrderedPlan, filter: FinalizersFilter[F]): DIResourceBase[F, Either[FailedProvision[F], Locator]]

  final def produceF[F[_]: TagK: DIEffect](plan: OrderedPlan): DIResourceBase[F, Locator] = {
    produceFX[F](plan, FinalizersFilter.all[F])
  }

  final def produceDetailedF[F[_]: TagK: DIEffect](plan: OrderedPlan): DIResourceBase[F, Either[FailedProvision[F], Locator]] = {
    produceDetailedFX[F](plan, FinalizersFilter.all[F])
  }

  final def produce(plan: OrderedPlan): DIResourceBase[Identity, Locator] = produceF[Identity](plan)

  final def produceDetailed(plan: OrderedPlan): DIResourceBase[Identity, Either[FailedProvision[Identity], Locator]] = produceDetailedF[Identity](plan)

  final def produceUnsafeF[F[_]: TagK: DIEffect](plan: OrderedPlan): F[Locator] = {
    val resource = produceF[F](plan)
    resource.acquire.map(resource.extract)
  }
  final def produceUnsafe(plan: OrderedPlan): Locator = produceUnsafeF[Identity](plan)
}
