package izumi.distage.framework.services

import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.services.ResourceRewriter.RewriteRules
import izumi.distage.model.definition.BootstrapModuleDef
import izumi.distage.model.planning.PlanningHook
import izumi.distage.planning.extensions.GraphDumpBootstrapModule

class BootstrapPlatformModule(options: PlanningOptions) extends BootstrapModuleDef {
  if (options.addGraphVizDump) {
    include(new GraphDumpBootstrapModule())
  }

  make[RewriteRules]
    .fromValue(options.rewriteRules)
  many[PlanningHook]
    .add[ResourceRewriter]
}
