package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class ModuleMergeException(message: String, val badKeys: Set[RuntimeDIUniverse.DIKey]) extends DIException(message, null)
