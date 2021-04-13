package izumi.distage.model.plan.repr

import izumi.distage.model.plan.OrderedPlan
import izumi.functional.Renderable

@deprecated("should be removed with OrderedPlan", "13/04/2021")
object CompactOrderedPlanFormatter extends Renderable[OrderedPlan] {
  override def render(plan: OrderedPlan): String = {
    val minimizer = KeyMinimizer(plan.keys, DIRendering.colorsEnabled)
    val tf = TypeFormatter.minimized(minimizer)
    val kf = KeyFormatter.minimized(minimizer)
    val opFormatter = OpFormatter(kf, tf, DIRendering.colorsEnabled)

    plan.steps.map(opFormatter.format).mkString("\n")
  }
}
