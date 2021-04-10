package izumi.distage.model.plan

import izumi.distage.model.PlannerInput
import izumi.distage.model.exceptions.DIBugException
import izumi.distage.model.plan.repr.{CompactOrderedPlanFormatter, DIPlanCompactFormatter, DIRendering, DepTreeRenderer, KeyFormatter, KeyMinimizer, OpFormatter, TypeFormatter}
import izumi.distage.model.plan.topology.DepTreeNode.DepNode
import izumi.distage.model.plan.topology.DependencyGraph
import izumi.distage.model.reflection.DIKey
import izumi.functional.Renderable
import izumi.fundamentals.graphs.DG
import izumi.fundamentals.graphs.tools.{Toposort, ToposortLoopBreaker}

case class DIPlan(plan: DG[DIKey, ExecutableOp], input: PlannerInput)

object DIPlan {
  @inline implicit final def defaultFormatter: Renderable[DIPlan] = DIPlanCompactFormatter

  implicit class DIPlanSyntax(plan: DIPlan) {
    def render()(implicit ev: Renderable[DIPlan]): String = ev.render(plan)
    def renderDeps(node: DepNode): String = new DepTreeRenderer(node, plan.plan.meta.nodes).render()
    def renderAllDeps(): String = {
      val effectiveRoots = plan.plan.roots
      val g = new DependencyGraph(plan.plan.predecessors.links, DependencyGraph.DependencyKind.Depends)
      effectiveRoots.map(root => g.tree(root)).map(renderDeps).mkString("\n")
    }
  }
}

