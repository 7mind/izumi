package com.github.pshirshov.izumi.distage.planning.extensions

import com.github.pshirshov.izumi.distage.model.definition.BootstrapModuleDef
import com.github.pshirshov.izumi.distage.model.planning.PlanningObserver

class GraphDumpBootstrapModule extends BootstrapModuleDef {
  many[PlanningObserver].add[GraphDumpObserver]
}
