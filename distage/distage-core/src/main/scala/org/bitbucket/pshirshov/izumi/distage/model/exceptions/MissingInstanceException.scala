package org.bitbucket.pshirshov.izumi.distage.model.exceptions

import org.bitbucket.pshirshov.izumi.distage.model.DIKey

class MissingInstanceException(message: String, val key: DIKey) extends DIException(message, null)


