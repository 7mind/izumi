package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.provisioning.NewObjectOp
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class TriedToAddSetIntoSetException(message: String, val target: RuntimeDIUniverse.DIKey, val result: NewObjectOp) extends DIException(message, null)
