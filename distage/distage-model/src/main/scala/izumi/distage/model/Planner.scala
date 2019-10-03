package izumi.distage.model

import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan._
import izumi.distage.model.reflection.universe
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
  final case class GCRoots(roots: Set[DIKey]) extends GCMode {
    assert(roots.nonEmpty, "GC roots set cannot be empty")
    override def toSet: Set[universe.RuntimeDIUniverse.DIKey] = roots
  }
  case object NoGC extends GCMode {
    override def toSet: Set[RuntimeDIUniverse.DIKey] = Set.empty
  }

  def apply(key: DIKey, more: DIKey*): GCMode = GCRoots(more.toSet + key)
}


/**
  * Input for [[Planner]]
  *
  * @param bindings Bindings probably created using [[izumi.distage.model.definition.ModuleDef]] DSL
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

case class SplittedPlan(subplan: OrderedPlan, subRoots: Set[DIKey], primary: OrderedPlan, reducedModule: ModuleBase)


trait Planner {
  def plan(input: PlannerInput): OrderedPlan

  def merge(a: AbstractPlan, b: AbstractPlan): OrderedPlan

  // plan lifecycle
  def prepare(input: PlannerInput): DodgyPlan

  def freeze(plan: DodgyPlan): SemiPlan

  def finish(semiPlan: SemiPlan): OrderedPlan

  final def plan(input: ModuleBase, gcMode: GCMode): OrderedPlan = plan(PlannerInput(input, gcMode))

  final def splitExistingPlan(appModule: ModuleBase, primaryRoots: Set[DIKey], disabled: Set[DIKey], primaryPlan: OrderedPlan)(extractSubRoots: OrderedPlan => Set[DIKey]): SplittedPlan = {
    assert(primaryRoots.diff(primaryPlan.keys).isEmpty)
    // here we extract integration checks out of our shared components plan and build it
    val extractedRoots = extractSubRoots(primaryPlan)

    val extractedSubplan = if (extractedRoots.nonEmpty) {
      // exclude runtime
      plan(PlannerInput(appModule, extractedRoots))
    } else {
      OrderedPlan.empty
    }

    val reduced = appModule.drop(extractedSubplan.keys)

    // and here we build our final plan for shared components, with integration components excluded
    val primaryPlanWithoutExtractedPart = if (extractedRoots.nonEmpty || disabled.nonEmpty) {
      val partsLeft = primaryRoots -- extractedRoots -- disabled
      if (partsLeft.isEmpty) {
        OrderedPlan.empty
      } else {
        plan(PlannerInput(reduced, partsLeft))
      }
    } else {
      primaryPlan
    }

    SplittedPlan(extractedSubplan, extractedRoots, primaryPlanWithoutExtractedPart, reduced)
  }

  final def splitPlan(appModule: ModuleBase, primaryRoots: Set[DIKey])(extractSubRoots: OrderedPlan => Set[DIKey]): SplittedPlan = {
    val primaryPlan = if (primaryRoots.nonEmpty) {
      plan(PlannerInput(appModule, primaryRoots))
    } else {
      OrderedPlan.empty
    }

    splitExistingPlan(appModule, primaryRoots, Set.empty, primaryPlan)(extractSubRoots)
  }
}
