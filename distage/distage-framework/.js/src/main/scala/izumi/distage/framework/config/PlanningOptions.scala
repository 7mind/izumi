package izumi.distage.framework.config

/**
  * @param warnOnCircularDeps print a warning when a circular dependency is detected or a proxy is generated
  */
final case class PlanningOptions(
  warnOnCircularDeps: Boolean
)
object PlanningOptions {
  def default = PlanningOptions(warnOnCircularDeps = true)
}
