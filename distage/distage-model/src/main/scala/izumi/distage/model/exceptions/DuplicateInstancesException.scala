package izumi.distage.model.exceptions

import izumi.distage.model.reflection.universe.RuntimeDIUniverse

case class DuplicateInstancesException(key: RuntimeDIUniverse.DIKey) extends DIException(
  s"Cannot continue, key is already in the object graph: $key"
  , null
)




