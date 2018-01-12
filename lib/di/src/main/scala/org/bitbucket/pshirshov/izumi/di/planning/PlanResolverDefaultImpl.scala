package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.definition.ContextDefinition
import org.bitbucket.pshirshov.izumi.di.model.exceptions.UntranslatablePlanException
import org.bitbucket.pshirshov.izumi.di.model.plan.DodgyOp.{Nop, Statement}
import org.bitbucket.pshirshov.izumi.di.model.plan.{DodgyPlan, FinalPlan, FinalPlanImmutableImpl}




class PlanResolverDefaultImpl extends PlanResolver {
  override def resolve(steps: DodgyPlan, definition: ContextDefinition): FinalPlan = {
    val (goodSteps, badSteps) = steps.steps.filterNot(_.isInstanceOf[Nop]).partition(_.isInstanceOf[Statement])

    if (badSteps.nonEmpty) {
      throw new UntranslatablePlanException(s"Cannot translate untranslatable (with default policy, feel free to jerk at this point): $badSteps", badSteps)
    }

    val ops = goodSteps.collect { case op: Statement => op.op }
    val plan = new FinalPlanImmutableImpl(ops, definition)

    System.err.println("=" * 60 + " Final Plan " + "=" * 60)
    System.err.println(s"$plan")
    System.err.println("\n")
    plan
  }
}
