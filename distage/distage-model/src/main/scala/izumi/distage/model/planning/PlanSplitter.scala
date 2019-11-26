package izumi.distage.model.planning

import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan.TriSplittedPlan.Subplan
import izumi.distage.model.plan.{OrderedPlan, TriSplittedPlan}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.distage.model.{Planner, PlannerInput}

trait PlanSplitter {
  this: Planner =>
  final def triSplitPlan(appModule: ModuleBase, primaryRoots: Set[DIKey])(extractSubRoots: OrderedPlan => Set[DIKey]): TriSplittedPlan = {
    val rewritten = rewrite(appModule)
    val primaryPlan = toSubplanNoRewrite(rewritten, primaryRoots)

    // here we extract integration checks out of our shared components plan and build it
    val subplanRoots = extractSubRoots(primaryPlan)
    triPlan(rewritten, primaryPlan, primaryRoots, subplanRoots)
  }

  final def triPlan(appModule: ModuleBase, primaryRoots: Set[DIKey], subplanRoots: Set[DIKey]): TriSplittedPlan = {
    val primaryPlan = toSubplanNoRewrite(appModule, primaryRoots)
    triPlan(appModule, primaryPlan, primaryRoots, subplanRoots)
  }

  private final def triPlan(appModule: ModuleBase, extractedPrimaryPlan: OrderedPlan, primaryRoots: Set[DIKey], subplanRoots: Set[DIKey]): TriSplittedPlan = {
    assert(primaryRoots.diff(extractedPrimaryPlan.keys).isEmpty)
    val extractedSubplan = toSubplanNoRewrite(appModule, subplanRoots)

    val sharedKeys = extractedSubplan.index.keySet.intersect(extractedPrimaryPlan.index.keySet)
    val sharedPlan = toSubplanNoRewrite(appModule, sharedKeys)

    val noSharedComponentsModule = appModule.drop(sharedKeys)
    val primplan = extractedPrimaryPlan.replaceWithImports(sharedKeys)

    val subModule = noSharedComponentsModule.drop(primplan.index.keySet)
    val subplan = extractedSubplan.replaceWithImports(sharedKeys)

    val sharedModule = appModule.preserveOnly(sharedPlan.index.keySet)
    val primModule = noSharedComponentsModule.drop(subplan.index.keySet)

    TriSplittedPlan(
      Subplan(subplan, subplanRoots, subModule),
      Subplan(primplan, primaryRoots, primModule),
      Subplan(sharedPlan, sharedKeys, sharedModule),
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
