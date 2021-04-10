package izumi.distage.model.plan.repr

import izumi.distage.model.exceptions.DIBugException
import izumi.distage.model.plan.DIPlan
import izumi.distage.model.reflection.DIKey
import izumi.functional.Renderable
import izumi.fundamentals.graphs.tools.{Toposort, ToposortLoopBreaker}

object DIPlanCompactFormatter extends Renderable[DIPlan] {

  import izumi.fundamentals.platform.strings.IzString._

  override def render(plan: DIPlan): String = {
    val minimizer = KeyMinimizer(plan.plan.meta.nodes.keySet, DIRendering.colorsEnabled)
    val tf = TypeFormatter.minimized(minimizer)
    val kf = KeyFormatter.minimized(minimizer)
    val opFormatter = OpFormatter(kf, tf, DIRendering.colorsEnabled)

    Toposort.cycleBreaking(plan.plan.predecessors, ToposortLoopBreaker.breakOn[DIKey](_.headOption)) match {
      case Left(value) =>
        throw DIBugException(s"BUG: toposort failed during plan rendering: $value")
      case Right(value) =>
        value.map(k => opFormatter.format(plan.plan.meta.nodes(k))).mkString("\n").listing()
    }
  }
}
