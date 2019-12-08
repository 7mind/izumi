package izumi.distage.model.plan.repr

import izumi.distage.model.plan.OrderedPlan
import izumi.functional.Renderable

object CompactOrderedPlanFormatter extends Renderable[OrderedPlan] {
  override def render(plan: OrderedPlan): String = {
    val minimizer = KeyMinimizer(plan.keys)
    val tf = TypeFormatter.minimized(minimizer)
    val kf = KeyFormatter.minimized(minimizer)

    plan.steps.map(OpFormatter(kf, tf).format).mkString("\n")
  }
}
