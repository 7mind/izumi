package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey

trait GCRootPredicate {
  def isRoot(key: DIKey): Boolean
}

object GCRootPredicate {
  def apply(roots: Set[DIKey]): GCRootPredicate = roots.contains
}
