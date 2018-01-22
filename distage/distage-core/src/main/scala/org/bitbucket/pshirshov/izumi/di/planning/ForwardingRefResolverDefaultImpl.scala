package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.model.plan.DodgyPlan
import org.bitbucket.pshirshov.izumi.di.model.plan.ExecutableOp.{ProxyOp, WiringOp}



class ForwardingRefResolverDefaultImpl
(
  protected val planAnalyzer: PlanAnalyzer
) extends ForwardingRefResolver {
  override def resolve(plan: DodgyPlan): DodgyPlan = {
    val statements = plan.statements
    val reftable = planAnalyzer.computeFwdRefTable(statements)

    import reftable._

    val resolvedSteps = plan.steps.flatMap {
      case step: WiringOp if dependencies.contains(step.target) =>
        Seq(ProxyOp.MakeProxy(step, dependencies(step.target), dependants(step.target)))

      case step: WiringOp if dependants.contains(step.target) =>
        Seq(ProxyOp.InitProxies(step, dependencies(step.target), dependants(step.target)))

      case step =>
        Seq(step)
    }

    plan.copy(steps = resolvedSteps)
  }
}
