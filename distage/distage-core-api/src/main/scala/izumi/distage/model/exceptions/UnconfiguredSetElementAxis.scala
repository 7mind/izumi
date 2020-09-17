package izumi.distage.model.exceptions

import izumi.distage.model.reflection.DIKey

class UnconfiguredSetElementAxis(message: String, val elementKey: DIKey, val setKey: DIKey) extends DIException(message)
