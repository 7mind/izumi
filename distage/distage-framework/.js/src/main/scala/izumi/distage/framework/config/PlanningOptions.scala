package izumi.distage.framework.config

/**
  * @param addGraphVizDump    dump Plan to a graphviz file in ./target/ directory
  * @param warnOnCircularDeps print a warning when a circular dependency is detected or a proxy is generated
  */
final case class PlanningOptions(
  warnOnCircularDeps: Boolean = true,
)
