package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.model.exceptions.UntranslatablePlanException
import org.bitbucket.pshirshov.izumi.di.model.plan.DodgyOp.{Nop, Statement}
import org.bitbucket.pshirshov.izumi.di.model.plan.{DodgyPlan, ReadyPlan, ReadyPlanImmutableImpl}




class PlanResolverDefaultImpl extends PlanResolver {
  override def resolve(steps: DodgyPlan): ReadyPlan = {
    val (goodSteps, badSteps) = steps.steps.filterNot(_.isInstanceOf[Nop]).partition(_.isInstanceOf[Statement])

    if (badSteps.nonEmpty) {
      throw new UntranslatablePlanException(s"Cannot translate untranslatable (with default policy, feel free to jerk at this point): $badSteps", badSteps)
    }

    val plan = new ReadyPlanImmutableImpl(goodSteps.collect { case op: Statement => op.op })

    System.err.println("=" * 60 + " Final Plan " + "=" * 60)
    System.err.println(s"$plan")
    System.err.println("\n")
    plan
  }
}
