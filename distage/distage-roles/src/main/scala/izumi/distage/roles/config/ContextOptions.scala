package izumi.distage.roles.config

import izumi.distage.config.ConfigInjectionOptions
import izumi.distage.roles.services.ResourceRewriter.RewriteRules

/**
  * @param addGraphVizDump dump plan to graphviz file in ./target/
  * @param warnOnCircularDeps
  * @param rewriteRules
  * @param configInjectionOptions
  */
case class ContextOptions(
                           addGraphVizDump: Boolean = false,
                           warnOnCircularDeps: Boolean = true,
                           rewriteRules: RewriteRules = RewriteRules(),
                           configInjectionOptions: ConfigInjectionOptions = ConfigInjectionOptions(),
                         )
