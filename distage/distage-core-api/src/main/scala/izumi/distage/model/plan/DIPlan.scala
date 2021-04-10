package izumi.distage.model.plan

import izumi.distage.model.PlannerInput
import izumi.distage.model.plan.repr.{DIPlanCompactFormatter, DepTreeRenderer}
import izumi.distage.model.plan.topology.DependencyGraph
import izumi.distage.model.reflection.DIKey
import izumi.functional.Renderable
import izumi.fundamentals.graphs.DG

case class DIPlan(plan: DG[DIKey, ExecutableOp], input: PlannerInput)

object DIPlan {
  @inline implicit final def defaultFormatter: Renderable[DIPlan] = DIPlanCompactFormatter

  implicit class DIPlanSyntax(plan: DIPlan) {
    def render()(implicit ev: Renderable[DIPlan]): String = ev.render(plan)
    def renderDeps(key: DIKey): String = {
      new DepTreeRenderer(dg.tree(key), plan.plan.meta.nodes).render()
    }
    def renderAllDeps(): String = {
      val effectiveRoots = plan.plan.roots
      effectiveRoots.map(renderDeps).mkString("\n")
    }
    private lazy val dg =  new DependencyGraph(plan.plan.predecessors.links, DependencyGraph.DependencyKind.Depends)
  }
}

