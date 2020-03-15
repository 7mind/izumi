package izumi.distage.model.exceptions

import izumi.distage.model.reflection.DIKey

class DuplicateKeysException(message: String, val keys: Map[DIKey, Int]) extends DIException(message)






