package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.provisioning.OpResult
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class TriedToAddSetIntoSetException(message: String, target: RuntimeDIUniverse.DIKey, result: OpResult) extends DIException(message, null)
