package izumi.distage.model.planning

import izumi.distage.model.{Planner, PlannerInput}
import izumi.distage.model.definition.{Activation, ModuleBase}
import izumi.distage.model.plan.{OrderedPlan, TriSplittedPlan}
import izumi.distage.model.reflection._

class PlanSplittingOps(
  planner: Planner,
  analyzer: PlanAnalyzer,
) {

  final def trisectByKeys(
    activation: Activation,
    appModule: ModuleBase,
    primaryRoots: Set[DIKey],
  )(extractSubRoots: OrderedPlan => (Set[DIKey], Set[DIKey])
  ): TriSplittedPlan = {
    val rewritten = planner.rewrite(appModule)
    val basePlan = toSubplanNoRewrite(activation, rewritten, primaryRoots)

    // here we extract integration checks out of our shared components plan and build it
    val (subplanRoots1, subplanRoots2) = extractSubRoots(basePlan)
    trisect(activation, rewritten, basePlan, primaryRoots, subplanRoots1, subplanRoots2)
  }

  final def trisectByRoots(
    activation: Activation,
    appModule: ModuleBase,
    primaryRoots: Set[DIKey],
    subplanRoots1: Set[DIKey],
    subplanRoots2: Set[DIKey],
  ): TriSplittedPlan = {
    trisectByKeys(activation, appModule, primaryRoots)(_ => (subplanRoots1, subplanRoots2))
  }

  private[this] final def trisect(
    activation: Activation,
    appModule: ModuleBase,
    baseplan: OrderedPlan,
    primaryRoots: Set[DIKey],
    subplanRoots1: Set[DIKey],
    subplanRoots2: Set[DIKey],
  ): TriSplittedPlan = {
    assert(primaryRoots.diff(baseplan.keys).isEmpty)
    val subplanRoots = subplanRoots1 ++ subplanRoots2
    val extractedSubplan = truncateOrReplan(activation, appModule, baseplan, subplanRoots)

    val sharedKeys = extractedSubplan.index.keySet.intersect(baseplan.index.keySet)
    val sharedPlan = truncateOrReplan(activation, appModule, extractedSubplan, sharedKeys)

    val primplan = baseplan.replaceWithImports(sharedKeys)
    val subplan = extractedSubplan.replaceWithImports(sharedKeys)

    assert(subplan.declaredRoots == subplanRoots)
    assert(primplan.declaredRoots == primaryRoots)
    assert(sharedPlan.declaredRoots == sharedKeys)

    TriSplittedPlan(
      side = subplan,
      primary = primplan,
      shared = sharedPlan,
      sideRoots1 = subplanRoots1,
      sideRoots2 = subplanRoots2,
    )
  }

  private[this] final def truncateOrReplan(activation: Activation, appModule: ModuleBase, basePlan: OrderedPlan, subplanKeys: Set[DIKey]): OrderedPlan = {
    val isSubset = subplanKeys.diff(basePlan.index.keySet).isEmpty
    if (isSubset) {
//      //truncate(basePlan, subplanKeys)
//      ???
      // TODO: this can be optimized by truncation instead of replanning
      toSubplanNoRewrite(activation, appModule, subplanKeys)

    } else {
      toSubplanNoRewrite(activation, appModule, subplanKeys)
    }

  }

  private[this] final def toSubplanNoRewrite(activation: Activation, appModule: ModuleBase, extractedRoots: Set[DIKey]): OrderedPlan = {
    if (extractedRoots.nonEmpty) {
      // exclude runtime
      planner.planNoRewrite(PlannerInput(appModule, activation, extractedRoots)).toOrdered(analyzer)
    } else {
      OrderedPlan.empty
    }
  }

}
