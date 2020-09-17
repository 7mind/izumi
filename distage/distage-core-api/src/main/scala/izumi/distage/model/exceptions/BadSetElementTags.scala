package izumi.distage.model.exceptions

import izumi.distage.model.reflection.DIKey

class BadSetElementTags(message: String, val elementKey: DIKey, val setKey: DIKey) extends DIException(message)
