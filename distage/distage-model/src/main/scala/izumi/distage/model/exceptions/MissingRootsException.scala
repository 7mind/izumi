package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class MissingRootsException(message: String, val roots: Set[RuntimeDIUniverse.DIKey]) extends DIException(message, null)
