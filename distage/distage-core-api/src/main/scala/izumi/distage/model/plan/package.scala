package izumi.distage.model

package object plan {

  @deprecated("Renamed to `Plan`", "1.1.0")
  type DIPlan = Plan
  @deprecated("Renamed to `Plan`", "1.1.0")
  lazy val DIPlan: Plan.type = Plan

  @deprecated("Replaced by `Plan`", "1.1.0")
  type OrderedPlan = Plan
  @deprecated("Replaced by `Plan`", "1.1.0")
  lazy val OrderedPlan: Plan.type = Plan

  @deprecated("Replaced by `Plan`", "1.1.0")
  type SemiPlan = Plan
  @deprecated("Replaced by `Plan`", "1.1.0")
  lazy val SemiPlan: Plan.type = Plan

  @deprecated("Replaced by `Plan`", "1.1.0")
  type AbstractPlan = Plan
  @deprecated("Replaced by `Plan`", "1.1.0")
  lazy val AbstractPlan: Plan.type = Plan

}
