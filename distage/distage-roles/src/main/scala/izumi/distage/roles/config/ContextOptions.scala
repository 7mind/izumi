package izumi.distage.roles.config

import izumi.distage.config.ConfigInjectionOptions
import izumi.distage.roles.services.ResourceRewriter.RewriteRules

case class ContextOptions(
                           addGvDump: Boolean,
                           warnOnCircularDeps: Boolean,
                           rewriteRules: RewriteRules,
                           configInjectionOptions: ConfigInjectionOptions,
                         )
