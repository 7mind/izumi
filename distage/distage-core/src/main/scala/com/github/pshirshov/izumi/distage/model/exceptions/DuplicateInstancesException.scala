package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.DIKey

class DuplicateInstancesException(message: String, val key: DIKey) extends DIException(message, null)



