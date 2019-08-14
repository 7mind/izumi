package com.github.pshirshov.izumi.distage.provisioning.strategies.cglib.exceptions

import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class CgLibCallException(message: String, val arguments: Seq[AnyRef], val expectedArgTypes: Seq[RuntimeDIUniverse.DIKey]) extends DIException(message, null)
