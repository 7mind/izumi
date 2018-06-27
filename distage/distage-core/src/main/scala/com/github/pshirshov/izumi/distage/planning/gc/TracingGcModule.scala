package com.github.pshirshov.izumi.distage.planning.gc

import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.distage.model.planning.{DIGarbageCollector, GCRootPredicate, PlanningHook}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class TracingGcModule(roots: Set[RuntimeDIUniverse.DIKey]) extends ModuleDef {
  make[DIGarbageCollector].from(TracingDIGC)
  make[GCRootPredicate].from(new GCRootPredicateSet(roots))
  many[PlanningHook]
    .add[GCHook]
}
