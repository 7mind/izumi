package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse

class MissingInstanceException(message: String, val key: RuntimeUniverse.DIKey) extends DIException(message, null)


