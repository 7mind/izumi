package izumi.distage.planning.sequential

import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, InstantiationOp, WiringOp}
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.graphs.DG

object CycleTools {

  implicit class PlanIndexExts(plan: DG[DIKey, ExecutableOp.SemiplanOp]) {
    def allDirectUsers(target: DIKey): Set[DIKey] = {
      plan.successors.links(target)
    }

    def countByNameUsages(scope: Map[DIKey, Set[DIKey]], target: DIKey): Int = {
      usageClassification(scope, target).count(_._2)
    }

    // this is a very rare, impractical case
    def allUsagesAreByName(scope: Map[DIKey, Set[DIKey]], target: DIKey): Boolean = {
      usageClassification(scope, target).forall(_._2)
    }

    private def usageClassification(scope: Map[DIKey, Set[DIKey]], target: DIKey): Set[(DIKey, Boolean)] = {
      assert(scope(target).nonEmpty)
      val scopedUsers = scope.values.flatten.toSet
      assert(scope.keySet.forall(scopedUsers.contains))
      val usages = plan.allDirectUsers(target).intersect(scopedUsers)
      assert(usages.nonEmpty)
      val index = plan.meta.nodes
      val usedByOps = (usages + target).map(index.apply)

      val associations = usedByOps.flatMap {
        case op: InstantiationOp =>
          op match {
            case w: ExecutableOp.WiringOp =>
              w match {
                case _: WiringOp.CallProvider =>
                  Seq(w.target -> w.wiring.associations.filter(a => target.tpe <:< a.key.tpe).forall(a => a.isByName))
                case _: WiringOp.UseInstance =>
                  Seq(w.target -> false)
                case _: WiringOp.ReferenceKey =>
                  Seq(w.target -> false)
              }
            case o =>
              Seq(o.target -> false)
          }
        case _: ImportDependency =>
          Seq.empty
      }

      associations
    }
  }
}
