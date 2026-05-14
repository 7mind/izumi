package izumi.distage.model

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{Bifunctorized, IO2, Primitives2}
import izumi.distage.model.plan.Plan
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FinalizerFilter}
import izumi.reflect.TagKK

/** Executes instructions in [[izumi.distage.model.plan.Plan]] to produce a [[izumi.distage.model.Locator]]
  *
  * @throws izumi.distage.model.exceptions.runtime.ProvisioningException produce* methods raise this exception in `F` effect type on failure
  */
trait Producer {
  private[distage] def produceDetailedFX[F[+_, +_]: TagKK: IO2](plan: Plan, filter: FinalizerFilter[F]): Lifecycle[F, Throwable, Either[FailedProvision, Locator]]
  private[distage] final def produceFX[F[+_, +_]: TagKK: IO2: Primitives2](plan: Plan, filter: FinalizerFilter[F]): Lifecycle[F, Throwable, Locator] = {
    produceDetailedFX[F](plan, filter).evalMap(_.failOnFailure())
  }

  /** Produce [[izumi.distage.model.Locator]] interpreting effect- and resource-bindings into the provided `F` */
  final def produceCustomF[F[+_, +_]: TagKK: IO2: Primitives2](plan: Plan): Lifecycle[F, Throwable, Locator] = {
    produceFX[F](plan, FinalizerFilter.all[F])
  }
  final def produceDetailedCustomF[F[+_, +_]: TagKK: IO2](plan: Plan): Lifecycle[F, Throwable, Either[FailedProvision, Locator]] = {
    produceDetailedFX[F](plan, FinalizerFilter.all[F])
  }

  /** Produce [[izumi.distage.model.Locator]], supporting only effect- and resource-bindings in `Identity` */
  final def produceCustomIdentity(plan: Plan): Lifecycle[Bifunctorized.IdentityBifunctorized, Throwable, Locator] =
    produceCustomF[Bifunctorized.IdentityBifunctorized](plan)
  final def produceDetailedIdentity(plan: Plan): Lifecycle[Bifunctorized.IdentityBifunctorized, Throwable, Either[FailedProvision, Locator]] =
    produceDetailedCustomF[Bifunctorized.IdentityBifunctorized](plan)
}
