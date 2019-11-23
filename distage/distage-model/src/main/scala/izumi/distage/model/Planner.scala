package izumi.distage.model

import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan._
import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey

sealed trait GCMode {
  def toSet: Set[DIKey]

  final def ++(that: GCMode): GCMode = (this, that) match {
    case (GCMode.NoGC, _) => GCMode.NoGC
    case (_, GCMode.NoGC) => GCMode.NoGC
    case (GCMode.GCRoots(aRoots), GCMode.GCRoots(bRoots)) =>
      GCMode.GCRoots(aRoots ++ bRoots)
  }
}
object GCMode {
  def apply(key: DIKey, more: DIKey*): GCMode = GCRoots(more.toSet + key)

  final case class GCRoots(roots: Set[DIKey]) extends GCMode {
    assert(roots.nonEmpty, "GC roots set cannot be empty")
    override def toSet: Set[DIKey] = roots
  }
  case object NoGC extends GCMode {
    override def toSet: Set[DIKey] = Set.empty
  }
}

/**
  * Input for [[Planner]]
  *
  * @param bindings Bindings created by [[izumi.distage.model.definition.ModuleDef]] DSL
  * @param mode     Garbage collection roots.
  *
  *                 Garbage collector will remove all bindings that aren't direct or indirect dependencies
  *                 of the chosen root DIKeys from the plan - they will never be instantiated.
  *
  *                 If left empty, garbage collection will not be performed – that would be equivalent to
  *                 designating all DIKeys as roots.
  */
final case class PlannerInput(
                               bindings: ModuleBase,
                               mode: GCMode,
                             )

object PlannerInput {
  def noGc(bindings: ModuleBase): PlannerInput = {
    new PlannerInput(bindings, GCMode.NoGC)
  }

  def apply(bindings: ModuleBase, roots: Set[DIKey]): PlannerInput = {
    new PlannerInput(bindings, GCMode.GCRoots(roots))
  }
}

//case class SplittedPlan(
//                         subplan: OrderedPlan,
//                         subRoots: Set[DIKey],
//                         primary: OrderedPlan,
//                         reducedModule: ModuleBase
//                       )

case class Subplan(plan: OrderedPlan, roots: Set[DIKey], module: ModuleBase)

case class TriSplittedPlan(
                          side: Subplan,
                          primary: Subplan,
                          shared: Subplan,
                       )


/** Transforms [[ModuleBase]] into [[OrderedPlan]] */
trait Planner {
  def plan(input: PlannerInput): OrderedPlan

  def merge(a: AbstractPlan, b: AbstractPlan): OrderedPlan

  // plan lifecycle
  def planNoRewrite(input: PlannerInput): OrderedPlan

  def rewrite(module: ModuleBase): ModuleBase

  def prepare(input: PlannerInput): DodgyPlan

  def freeze(plan: DodgyPlan): SemiPlan

  def finish(semiPlan: SemiPlan): OrderedPlan

  final def plan(input: ModuleBase, gcMode: GCMode): OrderedPlan = plan(PlannerInput(input, gcMode))

  final def triSplitExistingPlan(_appModule: ModuleBase, primaryRoots: Set[DIKey], disabled: Set[DIKey], primaryPlan: OrderedPlan)(extractSubRoots: OrderedPlan => Set[DIKey]): TriSplittedPlan = {
    assert(primaryRoots.diff(primaryPlan.keys).isEmpty)

    val appModule = rewrite(_appModule)
    val ephemerals = _appModule.bindings.map(_.key).diff(appModule.bindings.map(_.key))

    // here we extract integration checks out of our shared components plan and build it
    val subplanRoots = extractSubRoots(primaryPlan)
    val extractedSubplan = toSubplan(appModule, subplanRoots)
    val extractedPrimaryPlan = toSubplan(appModule, primaryRoots)

    val sharedKeys = ephemerals ++ extractedSubplan.index.keySet.intersect(extractedPrimaryPlan.index.keySet)

    val sharedPlan = toSubplan(appModule, sharedKeys)
    val sharedModule = appModule.filter(sharedPlan.index.keySet)

    val noSharedComponentsModule = appModule.drop(sharedKeys)

    val subplan = toSubplan(noSharedComponentsModule, subplanRoots)
    val primplan = toSubplan(noSharedComponentsModule, primaryRoots)

//    val conflicts = primplan.index.keySet.intersect(subplan.index.keySet).filterNot(k => primplan.index(k).isInstanceOf[ExecutableOp.ImportDependency])
//    assert(conflicts.isEmpty, s"conflicts: ${conflicts}")

    TriSplittedPlan(
      Subplan(subplan, subplanRoots, noSharedComponentsModule.drop(primplan.index.keySet)),
      Subplan(primplan, primaryRoots, noSharedComponentsModule.drop(subplan.index.keySet)),
      Subplan(sharedPlan, sharedKeys, sharedModule),
    )
  }


  private def toSubplan(appModule: ModuleBase, extractedRoots: Set[RuntimeDIUniverse.DIKey]) = {
    if (extractedRoots.nonEmpty) {
      // exclude runtime
      planNoRewrite(PlannerInput(appModule, extractedRoots))
    } else {
      OrderedPlan.empty
    }
  }
  final def triSplitPlan(appModule: ModuleBase, primaryRoots: Set[DIKey])(extractSubRoots: OrderedPlan => Set[DIKey]): TriSplittedPlan = {
    val primaryPlan = toSubplan(appModule, primaryRoots)

    triSplitExistingPlan(appModule, primaryRoots, Set.empty, primaryPlan)(extractSubRoots)
  }

}
