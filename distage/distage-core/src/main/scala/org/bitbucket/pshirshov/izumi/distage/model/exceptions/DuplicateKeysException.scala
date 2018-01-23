package org.bitbucket.pshirshov.izumi.distage.model.exceptions

import org.bitbucket.pshirshov.izumi.distage.model.DIKey

class DuplicateKeysException(message: String, val keys: Map[DIKey, Int]) extends DIException(message, null)



