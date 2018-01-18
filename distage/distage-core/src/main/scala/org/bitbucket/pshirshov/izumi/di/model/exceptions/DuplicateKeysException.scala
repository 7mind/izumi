package org.bitbucket.pshirshov.izumi.di.model.exceptions

import org.bitbucket.pshirshov.izumi.di.model.DIKey

class DuplicateKeysException(message: String, val keys: Map[DIKey, Int]) extends DIException(message, null)



