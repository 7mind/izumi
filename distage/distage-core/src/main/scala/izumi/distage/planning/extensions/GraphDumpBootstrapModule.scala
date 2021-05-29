package izumi.distage.planning.extensions

import izumi.distage.model.definition.BootstrapModuleDef
import izumi.distage.model.planning.PlanningObserver

/**
  * Add this module to `Injector`'s parameters to enable GraphViz rendering of the plan.
  *
  * @see [[https://izumi.7mind.io/distage/debugging#graphviz-rendering GraphViz Rendering]]
  */
class GraphDumpBootstrapModule extends BootstrapModuleDef {
  // note: GraphDumpObserver doesn't work on Scala.js [due to file IO?]
  many[PlanningObserver]
    .add[GraphDumpObserver]
}

object GraphDumpBootstrapModule extends GraphDumpBootstrapModule {
  def apply(): GraphDumpBootstrapModule.type = this
}
