package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.model.plan.DodgyOp.Statement
import org.bitbucket.pshirshov.izumi.di.model.plan.DodgyPlan
import org.bitbucket.pshirshov.izumi.di.model.plan.ExecutableOp.{InitProxies, MakeProxy}



class ForwardingRefResolverDefaultImpl
(
  protected val planAnalyzer: PlanAnalyzer
) extends ForwardingRefResolver {
  override def resolve(plan: DodgyPlan): DodgyPlan = {
    val statements = plan.statements
    val reftable = planAnalyzer.computeFwdRefTable(statements)

    import reftable._

    val resolvedSteps = plan.steps.flatMap {
      case Statement(step) if dependencies.contains(step.target) =>
        Seq(Statement(MakeProxy(step, dependencies(step.target))))

      case Statement(step) if dependants.contains(step.target) =>
        Seq(Statement(InitProxies(step, dependants(step.target))))

      case step =>
        Seq(step)
    }

    DodgyPlan(plan.imports, plan.sets, resolvedSteps)
  }
}
