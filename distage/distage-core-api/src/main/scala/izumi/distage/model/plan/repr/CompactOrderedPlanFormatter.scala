package izumi.distage.model.plan.repr

import izumi.distage.constructors.DebugProperties
import izumi.distage.model.plan.OrderedPlan
import izumi.functional.Renderable
import izumi.fundamentals.platform.basics.IzBoolean.{all, any}
import izumi.fundamentals.platform.jvm.IzJvm



@deprecated("", "")
object CompactOrderedPlanFormatter extends Renderable[OrderedPlan] {

  override def render(plan: OrderedPlan): String = {
    val minimizer = KeyMinimizer(plan.keys, DIRendering.colorsEnabled)
    val tf = TypeFormatter.minimized(minimizer)
    val kf = KeyFormatter.minimized(minimizer)
    val opFormatter = OpFormatter(kf, tf, DIRendering.colorsEnabled)

    plan.steps.map(opFormatter.format).mkString("\n")
  }
}
