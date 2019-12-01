package izumi.distage.model.exceptions

import izumi.distage.model.reflection.universe.RuntimeDIUniverse

class ModuleMergeException(message: String, val badKeys: Set[RuntimeDIUniverse.DIKey]) extends DIException(message)
