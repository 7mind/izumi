package izumi.distage.model.plan

import izumi.distage.model.plan.repr._
import izumi.distage.model.plan.topology.PlanTopology
import izumi.distage.model.reflection._
import izumi.fundamentals.platform.strings.IzString.toRichString

/**
  * Linearized graph which is ready to be consumed by linear executors
  *
  * May contain cyclic dependencies resolved with proxies
  */
@deprecated("should be removed once we finish transition to parallel interpreter", "13/04/2021")
final case class OrderedPlan(
  steps: Vector[ExecutableOp],
  declaredRoots: Set[DIKey],
  topology: PlanTopology,
) {
  def keys: Set[DIKey] = {
    steps.map(_.target).toSet
  }

  /** Print while omitting package names for unambiguous types */
  override def toString: String = {
    val minimizer = KeyMinimizer(this.keys, DIRendering.colorsEnabled)
    val tf = TypeFormatter.minimized(minimizer)
    val kf = KeyFormatter.minimized(minimizer)
    val opFormatter = OpFormatter(kf, tf, DIRendering.colorsEnabled)

    this.steps.map(opFormatter.format).mkString("\n").listing()
  }
}
