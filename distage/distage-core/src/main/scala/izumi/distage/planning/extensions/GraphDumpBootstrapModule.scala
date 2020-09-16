package izumi.distage.planning.extensions

import izumi.distage.model.definition.BootstrapModuleDef
import izumi.distage.model.planning.PlanningObserver

class GraphDumpBootstrapModule extends BootstrapModuleDef {
  // GraphDumpObserver doesn't work on sjs

  many[PlanningObserver]
    .add[GraphDumpObserver]
}

object GraphDumpBootstrapModule {
  def apply(): GraphDumpBootstrapModule = new GraphDumpBootstrapModule()
}
