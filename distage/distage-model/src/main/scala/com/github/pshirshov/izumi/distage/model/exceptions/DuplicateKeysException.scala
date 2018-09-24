package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class DuplicateKeysException(message: String, val keys: Map[RuntimeDIUniverse.DIKey, Int]) extends DIException(message, null)






