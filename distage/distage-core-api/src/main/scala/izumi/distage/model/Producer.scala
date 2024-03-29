package izumi.distage.model

import izumi.distage.model.definition.Lifecycle
import izumi.functional.quasi.QuasiIO
import izumi.distage.model.plan.Plan
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FinalizerFilter}
import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.TagK

/** Executes instructions in [[izumi.distage.model.plan.Plan]] to produce a [[izumi.distage.model.Locator]]
  *
  * @throws izumi.distage.model.exceptions.runtime.ProvisioningException produce* methods raise this exception in `F` effect type on failure
  */
trait Producer {
  private[distage] def produceDetailedFX[F[_]: TagK: QuasiIO](plan: Plan, filter: FinalizerFilter[F]): Lifecycle[F, Either[FailedProvision, Locator]]
  private[distage] final def produceFX[F[_]: TagK: QuasiIO](plan: Plan, filter: FinalizerFilter[F]): Lifecycle[F, Locator] = {
    produceDetailedFX[F](plan, filter).evalMap(_.failOnFailure())
  }

  /** Produce [[izumi.distage.model.Locator]] interpreting effect- and resource-bindings into the provided `F` */
  final def produceCustomF[F[_]: TagK: QuasiIO](plan: Plan): Lifecycle[F, Locator] = {
    produceFX[F](plan, FinalizerFilter.all[F])
  }
  final def produceDetailedCustomF[F[_]: TagK: QuasiIO](plan: Plan): Lifecycle[F, Either[FailedProvision, Locator]] = {
    produceDetailedFX[F](plan, FinalizerFilter.all[F])
  }

  /** Produce [[izumi.distage.model.Locator]], supporting only effect- and resource-bindings in `Identity` */
  final def produceCustomIdentity(plan: Plan): Lifecycle[Identity, Locator] =
    produceCustomF[Identity](plan)
  final def produceDetailedIdentity(plan: Plan): Lifecycle[Identity, Either[FailedProvision, Locator]] =
    produceDetailedCustomF[Identity](plan)
}
