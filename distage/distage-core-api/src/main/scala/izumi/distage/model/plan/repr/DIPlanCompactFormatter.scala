package izumi.distage.model.plan.repr

import izumi.distage.model.plan.Plan
import izumi.functional.Renderable

object DIPlanCompactFormatter extends Renderable[Plan] {

  import izumi.fundamentals.platform.strings.IzString._

  override def render(plan: Plan): String = {
    val minimizer = KeyMinimizer(plan.plan.meta.nodes.keySet, DIRendering.colorsEnabled)
    val tf = TypeFormatter.minimized(minimizer)
    val kf = KeyFormatter.minimized(minimizer)
    val opFormatter = OpFormatter(kf, tf, DIRendering.colorsEnabled)

    plan.toposort.map(k => opFormatter.format(plan.plan.meta.nodes(k))).mkString("\n").listing()
  }
}
