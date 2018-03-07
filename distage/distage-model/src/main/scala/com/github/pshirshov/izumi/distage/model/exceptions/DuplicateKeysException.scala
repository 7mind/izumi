package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse

class DuplicateKeysException(message: String, val keys: Map[RuntimeUniverse.DIKey, Int]) extends DIException(message, null)





