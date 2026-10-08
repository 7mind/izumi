package izumi.distage.framework.config

import izumi.distage.framework.services.ResourceRewriter
import izumi.distage.framework.services.ResourceRewriter.RewriteRules

/**
  * @param addGraphVizDump    dump Plan to a graphviz file in ./target/ directory
  * @param warnOnCircularDeps print a warning when a circular dependency is detected or a proxy is generated
  * @param rewriteRules       allow rewriting of AutoCloseable bindings to ResourceBindings by [[ResourceRewriter]]
  *                           if disabled, AutoCloseables will NOT be deallocated
  */
final case class PlanningOptions(
  addGraphVizDump: Boolean,
  warnOnCircularDeps: Boolean,
  rewriteRules: RewriteRules,
)
object PlanningOptions {
  def default = PlanningOptions(addGraphVizDump = false, warnOnCircularDeps = true, ResourceRewriter.RewriteRules.enabled)
}
