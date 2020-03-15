package izumi.distage.model.exceptions

import izumi.distage.model.reflection.DIKey

class ModuleMergeException(message: String, val badKeys: Set[DIKey]) extends DIException(message)
