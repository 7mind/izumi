package izumi.distage.provisioning.strategies

import distage.{LocalContextImpl, LocatorRef, Planner, PlannerInput}
import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.distage.model.definition.errors.ProvisionerIssue.MissingInstance
import izumi.distage.model.plan.ExecutableOp.{AddRecursiveLocatorRef, WiringOp}
import izumi.distage.model.providers.Functoid
import izumi.distage.model.provisioning.strategies.ContextStrategy
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.functional.quasi.QuasiIO
import izumi.reflect.TagK

class ContextStrategyDefaultImpl(
  planner: Planner
) extends ContextStrategy {
  override def prepareContext[F[_]: TagK](
    context: ProvisioningKeyProvider,
    op: WiringOp.LocalContext,
  )(implicit F: QuasiIO[F]
  ): F[Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    val locatorKey = AddRecursiveLocatorRef.magicLocatorKey
    context.fetchKey(locatorKey, makeByName = false) match {
      case Some(value) =>
        val locatorRef = value.asInstanceOf[LocatorRef]
        val impl = op.wiring.provider.asInstanceOf[Functoid[F[Any]]]
        F.pure((for {
          subplan <- planner.plan(PlannerInput(op.wiring.module, context.plan.input.activation, impl.get.diKeys.toSet))
        } yield {
          val ctx = new LocalContextImpl[F, Any](op.wiring.externalKeys,locatorRef, subplan, impl, Map.empty)
          Seq(NewObjectOp.UseInstance(op.target, ctx))
        }).left.map(err => ProvisionerIssue.LocalContextPlanningFailed(op.target, err)))

      case None =>
        F.pure(Left(MissingInstance(locatorKey)))
    }
  }
}
