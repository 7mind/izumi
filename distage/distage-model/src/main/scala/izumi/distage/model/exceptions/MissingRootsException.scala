package izumi.distage.model.exceptions

import izumi.distage.model.reflection.universe.RuntimeDIUniverse

class MissingRootsException(message: String, val roots: Set[RuntimeDIUniverse.DIKey]) extends DIException(message)
