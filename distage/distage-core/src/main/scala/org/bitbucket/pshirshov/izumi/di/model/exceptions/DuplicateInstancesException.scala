package org.bitbucket.pshirshov.izumi.di.model.exceptions

import org.bitbucket.pshirshov.izumi.di.model.DIKey

class DuplicateInstancesException(message: String, val key: DIKey) extends DIException(message, null)



