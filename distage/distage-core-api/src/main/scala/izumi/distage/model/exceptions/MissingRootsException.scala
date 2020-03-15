package izumi.distage.model.exceptions

import izumi.distage.model.reflection.DIKey

class MissingRootsException(message: String, val roots: Set[DIKey]) extends DIException(message)
