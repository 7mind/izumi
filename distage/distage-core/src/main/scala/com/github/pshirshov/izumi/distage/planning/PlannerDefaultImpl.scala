package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning._
import com.github.pshirshov.izumi.distage.model.reflection.ReflectionProvider
import com.github.pshirshov.izumi.distage.model.{Planner, PlannerInput}
import com.github.pshirshov.izumi.functional.Value

class PlannerDefaultImpl
(
  protected val forwardingRefResolver: ForwardingRefResolver,
  protected val reflectionProvider: ReflectionProvider.Runtime,
  protected val sanityChecker: SanityChecker,
  protected val gc: DIGarbageCollector,
  protected val planningObserver: PlanningObserver,
  protected val planMergingPolicy: PlanMergingPolicy,
  protected val hook: PlanningHook,
  protected val bindingTranslator: BindingTranslator,
)
  extends Planner {


  override def plan(input: PlannerInput): OrderedPlan = {
    Value(prepare(input))
      .map(hook.phase00PostCompletion)
      .eff(planningObserver.onPhase00PlanCompleted)
      .map(planMergingPolicy.finalizePlan)
      .map(finish)
      .get
  }

  // TODO: add tests
  override def merge(a: AbstractPlan, b: AbstractPlan): OrderedPlan = {
    order(SemiPlan(a.definition ++ b.definition, (a.steps ++ b.steps).toVector, a.roots ++ b.roots))
  }

  private def prepare(input: PlannerInput): DodgyPlan = {
    hook
      .hookDefinition(input.bindings)
      .bindings
      .foldLeft(DodgyPlan.empty(input.bindings, input.roots)) {
        case (currentPlan, binding) =>
          Value(bindingTranslator.computeProvisioning(currentPlan, binding))
            .eff(sanityChecker.assertProvisionsSane)
            .map(planMergingPolicy.extendPlan(currentPlan, binding, _))
            .eff(planningObserver.onSuccessfulStep)
            .get
      }
  }

  def finish(semiPlan: SemiPlan): OrderedPlan = {
    Value(semiPlan)
      .map(planMergingPolicy.addImports)
      .eff(planningObserver.onPhase05PreGC)
      .map(doGC)
      .map(hook.phase10PostGC)
      .eff(planningObserver.onPhase10PostGC)
      .map(hook.phase20Customization)
      .eff(planningObserver.onPhase20Customization)
      .map(order)
      .get
  }

  private[this] def order(semiPlan: SemiPlan): OrderedPlan = {
    Value(semiPlan)
      .map(hook.phase45PreForwardingCleanup)
      .map(hook.phase50PreForwarding)
      .eff(planningObserver.onPhase50PreForwarding)
      .map(planMergingPolicy.reorderOperations)
      .map(forwardingRefResolver.resolve)
      .map(hook.phase90AfterForwarding)
      .eff(planningObserver.onPhase90AfterForwarding)
      .eff(sanityChecker.assertFinalPlanSane)
      .get
  }


  private[this] def doGC(semiPlan: SemiPlan): SemiPlan = {
    if (semiPlan.roots.nonEmpty) {
      gc.gc(semiPlan)
    } else {
      semiPlan
    }
  }


}
