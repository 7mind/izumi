package izumi.distage.model.planning

import izumi.distage.model.definition.{Activation, ModuleBase}
import izumi.distage.model.plan.{OrderedPlan, TriSplittedPlan}
import izumi.distage.model.reflection._
import izumi.distage.model.{Planner, PlannerInput}

trait PlanSplittingOps { this: Planner =>

  final def trisectByKeys(activation: Activation, appModule: ModuleBase, primaryRoots: Set[DIKey])(extractSubRoots: OrderedPlan => Set[DIKey]): TriSplittedPlan = {
    val rewritten = rewrite(appModule)
    val basePlan = toSubplanNoRewrite(activation, rewritten, primaryRoots)

    // here we extract integration checks out of our shared components plan and build it
    val subplanRoots = extractSubRoots(basePlan)
    trisect(activation, rewritten, basePlan, primaryRoots, subplanRoots)
  }

  final def trisectByRoots(activation: Activation, appModule: ModuleBase, primaryRoots: Set[DIKey], subplanRoots: Set[DIKey]): TriSplittedPlan = {
    trisectByKeys(activation, appModule, primaryRoots)(_ => subplanRoots)
  }

  private[this] final def trisect(
    activation: Activation,
    appModule: ModuleBase,
    baseplan: OrderedPlan,
    primaryRoots: Set[DIKey],
    subplanRoots: Set[DIKey],
  ): TriSplittedPlan = {
    assert(primaryRoots.diff(baseplan.keys).isEmpty)
    val extractedSubplan = truncateOrReplan(activation, appModule, baseplan, subplanRoots)

    val sharedKeys = extractedSubplan.index.keySet.intersect(baseplan.index.keySet)
    val sharedPlan = truncateOrReplan(activation, appModule, extractedSubplan, sharedKeys)

    val primplan = baseplan.replaceWithImports(sharedKeys)
    val subplan = extractedSubplan.replaceWithImports(sharedKeys)

    assert(subplan.declaredRoots == subplanRoots)
    assert(primplan.declaredRoots == primaryRoots)
    assert(sharedPlan.declaredRoots == sharedKeys)

    TriSplittedPlan(
      subplan,
      primplan,
      sharedPlan,
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
    toSubplan(activation, appModule, extractedRoots, planNoRewrite)
  }

  private[this] final def toSubplan(activation: Activation, appModule: ModuleBase, extractedRoots: Set[DIKey], plan: PlannerInput => OrderedPlan): OrderedPlan = {
    if (extractedRoots.nonEmpty) {
      // exclude runtime
      plan(PlannerInput(appModule, activation, extractedRoots))
    } else {
      OrderedPlan.empty
    }
  }
}
