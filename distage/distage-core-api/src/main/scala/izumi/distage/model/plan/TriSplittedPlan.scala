package izumi.distage.model.plan

import izumi.fundamentals.platform.strings.IzString._

/**
  * @param side    integrations, must be inhertied from the `shared` plan
  * @param primary plan producing roots, must be inherited from the `side` plan
  * @param shared  creates a part of the graph shared by both `side` and `primary` plans
  */
final case class TriSplittedPlan(
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
