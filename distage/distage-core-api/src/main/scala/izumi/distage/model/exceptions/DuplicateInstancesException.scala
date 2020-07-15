package izumi.distage.model.exceptions

import izumi.distage.model.reflection.DIKey

case class DuplicateInstancesException(key: DIKey)
  extends DIException(
    s"Cannot continue, key is already in the object graph: $key"
  )
