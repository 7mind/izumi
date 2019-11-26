package izumi.distage.model.plan

import izumi.distage.model.plan.TriSplittedPlan.Subplan
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.fundamentals.platform.strings.IzString._

case class TriSplittedPlan(
                            side: Subplan,
                            primary: Subplan,
                            shared: Subplan,
                          )

object TriSplittedPlan {
  case class Subplan(plan: OrderedPlan, roots: Set[DIKey])

  implicit class TriPlanEx(split: TriSplittedPlan) {
    def render(): String = {
      Seq(
        split.shared.plan.render().listing("Shared Plan"),
        split.side.plan.render().listing("Side Plan"),
        split.primary.plan.render().listing("Primary Plan"),
      ).mkString("\n")
    }
  }

}