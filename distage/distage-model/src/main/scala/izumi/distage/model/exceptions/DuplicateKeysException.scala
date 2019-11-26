package izumi.distage.model.exceptions

import izumi.distage.model.reflection.universe.RuntimeDIUniverse

class DuplicateKeysException(message: String, val keys: Map[RuntimeDIUniverse.DIKey, Int]) extends DIException(message, null)
