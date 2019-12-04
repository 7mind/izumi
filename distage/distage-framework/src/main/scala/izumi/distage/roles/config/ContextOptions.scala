package izumi.distage.roles.config

import izumi.distage.roles.services.ResourceRewriter.RewriteRules

/**
  * @param addGraphVizDump    dump Plan to a graphviz file in ./target/ directory
  * @param warnOnCircularDeps print a warning when a circular dependency is detected or a proxy is generated
  * @param rewriteRules       allow rewriting of AutoCloseable bindings to ResourceBindings by [[izumi.distage.roles.services.ResourceRewriter]]
  *                           if disabled, AutoCloseables will NOT be deallocated
  */
final case class ContextOptions(
                                 addGraphVizDump: Boolean = false,
                                 warnOnCircularDeps: Boolean = true,
                                 rewriteRules: RewriteRules = RewriteRules(),
                               )
