package izumi.distage.model.plan.impl

import izumi.distage.model.Locator
import izumi.distage.model.plan.ExecutableOp.ProxyOp.{InitProxy, MakeProxy}
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, ProxyOp, SemiplanOp}
import izumi.distage.model.plan.{AbstractPlan, GCMode, OrderedPlan, SemiPlan}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._

trait OrderedPlanOps {
  this: OrderedPlan =>
  /**
    * Be careful, don't use this method blindly, it can disrupt graph connectivity when used improperly.
    *
    * Proper usage assume that `keys` contains complete subgraph reachable from graph roots.
    */
  def replaceWithImports(keys: Set[DIKey]): OrderedPlan = {
    val newSteps = steps.flatMap {
      case s if keys.contains(s.target) =>
        val dependees = topology.dependees.direct(s.target)
        if (dependees.diff(keys).nonEmpty || declaredRoots.contains(s.target)) {
          val dependees = topology.dependees.transitive(s.target).diff(keys)
          Seq(ImportDependency(s.target, dependees, s.origin.toSynthetic))
        } else {
          Seq.empty
        }
      case s =>
        Seq(s)
    }

    OrderedPlan(
      newSteps,
      declaredRoots,
      topology.removeKeys(keys),
    )
  }

  override def resolveImports(f: PartialFunction[ImportDependency, Any]): OrderedPlan = {
    copy(steps = AbstractPlan.resolveImports(AbstractPlan.importToInstances(f), steps))
  }

  override def resolveImport[T: Tag](instance: T): OrderedPlan =
    resolveImports {
      case i if i.target == DIKey.get[T] =>
        instance
    }

  override def resolveImport[T: Tag](id: String)(instance: T): OrderedPlan = {
    resolveImports {
      case i if i.target == DIKey.get[T].named(id) =>
        instance
    }
  }

  override def locateImports(locator: Locator): OrderedPlan = {
    resolveImports(Function.unlift(i => locator.lookupLocal[Any](i.target)))
  }

  override def toSemi: SemiPlan = {
    val safeSteps: Seq[SemiplanOp] = steps.flatMap{
      case s: SemiplanOp =>
        Seq(s)
      case s: ProxyOp =>
        s match {
          case m: MakeProxy =>
            Seq(m.op)
          case _: InitProxy =>
            Seq.empty
        }
    }
    SemiPlan(safeSteps.toVector, GCMode.fromSet(declaredRoots))
  }
}
