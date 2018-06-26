package com.github.pshirshov.izumi.distage.planning.gc

import com.github.pshirshov.izumi.distage.model.planning.GCRootPredicate
import com.github.pshirshov.izumi.distage.model.reflection.universe
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class GCRootPredicateSet(roots: Set[RuntimeDIUniverse.DIKey]) extends GCRootPredicate {

  override def isRoot(key: universe.RuntimeDIUniverse.DIKey): Boolean = {
    roots.contains(key)
  }

}
