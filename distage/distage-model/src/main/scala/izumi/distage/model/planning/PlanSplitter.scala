package izumi.distage.model.planning

import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan.TriSplittedPlan.Subplan
import izumi.distage.model.plan.{OrderedPlan, TriSplittedPlan}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.distage.model.{Planner, PlannerInput}

trait PlanSplitter {
  this: Planner =>
  final def trisectByPredicate(appModule: ModuleBase, primaryRoots: Set[DIKey])(extractSubRoots: OrderedPlan => Set[DIKey]): TriSplittedPlan = {
    val rewritten = rewrite(appModule)
    val primaryPlan = toSubplanNoRewrite(rewritten, primaryRoots)

    // here we extract integration checks out of our shared components plan and build it
    val subplanRoots = extractSubRoots(primaryPlan)
    trisect(rewritten, primaryPlan, primaryRoots, subplanRoots)
  }

  final def trisectByRoots(appModule: ModuleBase, primaryRoots: Set[DIKey], subplanRoots: Set[DIKey]): TriSplittedPlan = {
    val primaryPlan = toSubplanNoRewrite(appModule, primaryRoots)
    trisect(appModule, primaryPlan, primaryRoots, subplanRoots)
  }

  private final def trisect(appModule: ModuleBase, extractedPrimaryPlan: OrderedPlan, primaryRoots: Set[DIKey], subplanRoots: Set[DIKey]): TriSplittedPlan = {
    assert(primaryRoots.diff(extractedPrimaryPlan.keys).isEmpty)
    val extractedSubplan = toSubplanNoRewrite(appModule, subplanRoots)

    val sharedKeys = extractedSubplan.index.keySet.intersect(extractedPrimaryPlan.index.keySet)
    val sharedPlan = toSubplanNoRewrite(appModule, sharedKeys)

    val primplan = extractedPrimaryPlan.replaceWithImports(sharedKeys)
    val subplan = extractedSubplan.replaceWithImports(sharedKeys)


    TriSplittedPlan(
      Subplan(subplan, subplanRoots),
      Subplan(primplan, primaryRoots),
      Subplan(sharedPlan, sharedKeys),
    )

  }

  private def toSubplanNoRewrite(appModule: ModuleBase, extractedRoots: Set[DIKey]): OrderedPlan = {
    toSubplan(appModule, extractedRoots, planNoRewrite)
  }

  private def toSubplan(appModule: ModuleBase, extractedRoots: Set[DIKey], plan: PlannerInput => OrderedPlan): OrderedPlan= {
    if (extractedRoots.nonEmpty) {
      // exclude runtime
      plan(PlannerInput(appModule, extractedRoots))
    } else {
      OrderedPlan.empty
    }
  }
}
