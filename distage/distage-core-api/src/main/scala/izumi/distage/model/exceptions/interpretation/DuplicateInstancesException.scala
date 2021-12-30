package izumi.distage.model.exceptions.interpretation

import izumi.distage.model.exceptions.DIException
import izumi.distage.model.reflection.DIKey

@deprecated("Needs to be removed", "20/10/2021")
case class DuplicateInstancesException(key: DIKey)
  extends DIException(
    s"Cannot continue, key is already in the object graph: $key"
  )
