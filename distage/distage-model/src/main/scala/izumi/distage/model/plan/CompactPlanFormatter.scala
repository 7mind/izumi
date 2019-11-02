package izumi.distage.model.plan

import izumi.distage.model.reflection.universe.RuntimeDIUniverse.{DIKey, SafeType}
import izumi.functional.Renderable

trait CompactPlanFormatter extends Renderable[OrderedPlan] {
  override def render(plan: OrderedPlan): String = {
    val minimizer = new KeyMinimizer(plan.keys)
    val tf: TypeFormatter = (key: SafeType) => minimizer.renderType(key)

    val kf: KeyFormatter = (key: DIKey) => minimizer.render(key)

    val opFormatter = new OpFormatter.Impl(kf, tf)

    plan.steps.map(opFormatter.format).mkString("\n")
  }
}

object CompactPlanFormatter {
  implicit object OrderedPlanFormatter extends CompactPlanFormatter
}
