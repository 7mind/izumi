package izumi.distage.model.planning

import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan.GCMode.WeaknessPredicate
import izumi.distage.model.plan.{OrderedPlan, TriSplittedPlan}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.distage.model.{Planner, PlannerInput}

trait PlanSplittingOps {
  this: Planner =>

  final def trisectByKeys(appModule: ModuleBase, primaryRoots: Set[DIKey], weaknessPredicate: WeaknessPredicate)(extractSubRoots: OrderedPlan => Set[DIKey]): TriSplittedPlan = {
    val rewritten = rewrite(appModule)
    val basePlan = toSubplanNoRewrite(rewritten, primaryRoots, weaknessPredicate)

    // here we extract integration checks out of our shared components plan and build it
    val subplanRoots = extractSubRoots(basePlan)
    trisect(rewritten, basePlan, primaryRoots, subplanRoots, weaknessPredicate)
  }

  final def trisectByRoots(appModule: ModuleBase, primaryRoots: Set[DIKey], subplanRoots: Set[DIKey], weaknessPredicate: WeaknessPredicate): TriSplittedPlan = {
    trisectByKeys(appModule, primaryRoots, weaknessPredicate)(_ => subplanRoots)
  }

  private[this] final def trisect(appModule: ModuleBase, baseplan: OrderedPlan, primaryRoots: Set[DIKey], subplanRoots: Set[DIKey], weaknessPredicate: WeaknessPredicate): TriSplittedPlan = {
    assert(primaryRoots.diff(baseplan.keys).isEmpty)
    val extractedSubplan = truncateOrReplan(appModule, baseplan, subplanRoots, weaknessPredicate)

    val sharedKeys = extractedSubplan.index.keySet.intersect(baseplan.index.keySet)
    val sharedPlan = truncateOrReplan(appModule, extractedSubplan, sharedKeys, weaknessPredicate)

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

  private[this] final def truncateOrReplan(appModule: ModuleBase, basePlan: OrderedPlan, subplanKeys: Set[DIKey], weaknessPredicate: WeaknessPredicate): OrderedPlan = {
    val isSubset = subplanKeys.diff(basePlan.index.keySet).isEmpty
    if (isSubset) {
      truncate(basePlan, subplanKeys)
    } else {
      toSubplanNoRewrite(appModule, subplanKeys, weaknessPredicate)
    }
  }

  private[this] final def toSubplanNoRewrite(appModule: ModuleBase, extractedRoots: Set[DIKey], weaknessPredicate: WeaknessPredicate): OrderedPlan = {
    toSubplan(appModule, extractedRoots, planNoRewrite, weaknessPredicate)
  }

  private[this] final def toSubplan(appModule: ModuleBase, extractedRoots: Set[DIKey], plan: PlannerInput => OrderedPlan, weaknessPredicate: WeaknessPredicate): OrderedPlan = {
    if (extractedRoots.nonEmpty) {
      // exclude runtime
      plan(PlannerInput(appModule, extractedRoots, weaknessPredicate))
    } else {
      OrderedPlan.empty
    }
  }
}
