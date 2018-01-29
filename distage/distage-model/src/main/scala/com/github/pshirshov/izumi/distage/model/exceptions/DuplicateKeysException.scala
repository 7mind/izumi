package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.references.DIKey

class DuplicateKeysException(message: String, val keys: Map[DIKey, Int]) extends DIException(message, null)





