package com.github.pshirshov.izumi.distage.planning.extensions

import com.github.pshirshov.izumi.distage.model.definition.BootstrapModuleDef
import com.github.pshirshov.izumi.distage.model.planning.PlanningObserver
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class GraphDumpBootstrapModule extends BootstrapModuleDef {
  many[Set[RuntimeDIUniverse.DIKey]].named("gc.roots")
  many[PlanningObserver].add[GraphObserver]

}
