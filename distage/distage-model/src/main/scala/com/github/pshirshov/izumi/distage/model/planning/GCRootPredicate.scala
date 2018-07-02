package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

trait GCRootPredicate {
  def isRoot(key: RuntimeDIUniverse.DIKey): Boolean
}


