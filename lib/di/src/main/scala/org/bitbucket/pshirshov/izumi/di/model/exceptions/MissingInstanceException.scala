package org.bitbucket.pshirshov.izumi.di.model.exceptions

import org.bitbucket.pshirshov.izumi.di.model.DIKey

class MissingInstanceException(message: String, val key: DIKey) extends DIException(message, null)


