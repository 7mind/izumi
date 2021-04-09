package izumi.distage.model.plan

import izumi.distage.model.PlannerInput
import izumi.distage.model.reflection.DIKey
import izumi.functional.Renderable
import izumi.fundamentals.graphs.DG

case class DIPlan(plan: DG[DIKey, ExecutableOp], input: PlannerInput)

object DIPlan {
  implicit class DIPlanSyntax(plan: DIPlan) {
    def render()(implicit ev: Renderable[DIPlan]): String = ev.render(plan)
  }
}
