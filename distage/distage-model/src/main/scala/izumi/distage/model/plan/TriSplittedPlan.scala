package izumi.distage.model.plan

import izumi.fundamentals.platform.strings.IzString._

case class TriSplittedPlan(
                            side: OrderedPlan,
                            primary: OrderedPlan,
                            shared: OrderedPlan,
                          )

object TriSplittedPlan {
  implicit class TriPlanEx(split: TriSplittedPlan) {
    def render(): String = {
      Seq(
        split.shared.render().listing("Shared Plan"),
        split.side.render().listing("Side Plan"),
        split.primary.render().listing("Primary Plan"),
      ).mkString("\n")
    }
  }

}