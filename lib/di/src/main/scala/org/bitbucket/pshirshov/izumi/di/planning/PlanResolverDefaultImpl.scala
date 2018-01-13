package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.definition.ContextDefinition
import org.bitbucket.pshirshov.izumi.di.model.exceptions.UntranslatablePlanException
import org.bitbucket.pshirshov.izumi.di.model.plan.DodgyOp.{Nop, Statement}
import org.bitbucket.pshirshov.izumi.di.model.plan.{DodgyPlan, FinalPlan, FinalPlanImmutableImpl}




class PlanResolverDefaultImpl(planningObsever: PlanningObsever) extends PlanResolver {
  override def resolve(plan: DodgyPlan, definition: ContextDefinition): FinalPlan = {
    val (goodSteps, badSteps) = plan.flatten.filterNot(_.isInstanceOf[Nop]).partition(_.isInstanceOf[Statement])

    if (badSteps.nonEmpty) {
      throw new UntranslatablePlanException(s"Cannot translate untranslatable (with default policy, feel free to jerk at this point): $badSteps", badSteps)
    }

    val ops = goodSteps.collect { case op: Statement => op.op }
    val finalPlan = new FinalPlanImmutableImpl(ops, definition)
    planningObsever.onFinalPlan(finalPlan)
    finalPlan
  }
}
