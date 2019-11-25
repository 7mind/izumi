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

  final def triSplitPlan(appModule: ModuleBase, primaryRoots: Set[DIKey])(extractSubRoots: OrderedPlan => Set[DIKey]): TriSplittedPlan = {
    val rewritten = rewrite(appModule)
    val primaryPlan = toSubplanNoRewrite(rewritten, primaryRoots)

    // here we extract integration checks out of our shared components plan and build it
    val subplanRoots = extractSubRoots(primaryPlan) //extractSubRoots(toSubplanNoRewrite(appModule, primaryRoots))
//    import izumi.fundamentals.platform.strings.IzString._
//    println(primaryPlan.render())
//    println(s"collected: ${subplanRoots.niceList()}")
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
//    val primplan = toSubplanNoRewrite(noSharedComponentsModule, primaryRoots)

//    println(s"removal: ${sharedKeys}")
    val subModule = noSharedComponentsModule.drop(primplan.index.keySet)
    val subplan = extractedSubplan.replaceWithImports(sharedKeys)

//    println(s"esp: ${extractedSubplan.render()}")
//    println(s"espf: ${subplan.render()}")

    //    val subplan = toSubplanNoRewrite(subModule, subplanRoots)

    //    val conflicts = primplan.index.keySet.intersect(subplan.index.keySet).filterNot(k => primplan.index(k).isInstanceOf[ExecutableOp.ImportDependency])
    //    assert(conflicts.isEmpty, s"conflicts: ${conflicts}")

    val sharedModule = appModule.preserveOnly(sharedPlan.index.keySet)
    val primModule = noSharedComponentsModule.drop(subplan.index.keySet)

    TriSplittedPlan(
      Subplan(subplan, subplanRoots, subModule),
      Subplan(primplan, primaryRoots, primModule),
      Subplan(sharedPlan, sharedKeys, sharedModule),
    )

  }

  private def toSubplanNoRewrite(appModule: ModuleBase, extractedRoots: Set[RuntimeDIUniverse.DIKey]): OrderedPlan = {
    toSubplan(appModule, extractedRoots, planNoRewrite)
  }

  private def toSubplan(appModule: ModuleBase, extractedRoots: Set[RuntimeDIUniverse.DIKey], plan: PlannerInput => OrderedPlan): OrderedPlan= {
    if (extractedRoots.nonEmpty) {
      // exclude runtime
      plan(PlannerInput(appModule, extractedRoots))
    } else {
      OrderedPlan.empty
    }
  }
}
