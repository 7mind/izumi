package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

case class DuplicateInstancesException(key: RuntimeDIUniverse.DIKey) extends DIException(
  s"Cannot continue, key is already in the object graph: $key"
  , null
)




