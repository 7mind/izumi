package com.github.pshirshov.izumi.distage.planning.gc

import com.github.pshirshov.izumi.distage.model.definition.BootstrapModuleDef
import com.github.pshirshov.izumi.distage.model.planning.{DIGarbageCollector, GCRootPredicate, PlanningHook}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class TracingGCModule(roots: Set[RuntimeDIUniverse.DIKey]) extends BootstrapModuleDef {
  make[DIGarbageCollector].from(TracingDIGC)
  make[GCRootPredicate]
    .from(GCRootPredicate(roots))

  many[RuntimeDIUniverse.DIKey].named("gc.roots")
    .addSet(roots)

  many[PlanningHook]
    .add[GCHook]
}
