package org.bitbucket.pshirshov.izumi.di.model.exceptions

import org.bitbucket.pshirshov.izumi.di.model.DIKey

class DuplicateKeysException(message: String, val keys: Seq[DIKey]) extends DIException(message, null)


