package izumi.distage.planning

import izumi.distage.model.plan.*
import izumi.distage.model.plan.ExecutableOp.ProxyOp.{InitProxy, MakeProxy}
import izumi.distage.model.plan.ExecutableOp.{CreateSet, ImportDependency, MonadicOp, WiringOp}
import izumi.distage.model.planning.PlanAnalyzer
import izumi.distage.model.reflection.*

import scala.annotation.nowarn

@nowarn("msg=Unused import")
class PlanAnalyzerDefaultImpl extends PlanAnalyzer {

  def requirements(op: ExecutableOp): Seq[(DIKey, Set[DIKey])] = {
    op match {
      case w: WiringOp =>
        Seq((op.target, w.wiring.requiredKeys))

      case w: MonadicOp =>
        Seq((op.target, Set(w.effectKey)))

      case c: CreateSet =>
        Seq((op.target, c.members))

      case _: MakeProxy =>
        Seq((op.target, Set.empty))

      case _: ImportDependency =>
        Seq((op.target, Set.empty))

      case i: InitProxy =>
        Seq((i.target, Set(i.proxy.target))) ++ requirements(i.proxy.op)
    }
  }
}
