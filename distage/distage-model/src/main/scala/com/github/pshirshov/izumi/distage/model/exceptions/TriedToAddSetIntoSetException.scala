package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.provisioning.ContextAssignment
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class TriedToAddSetIntoSetException(message: String, val target: RuntimeDIUniverse.DIKey, val result: ContextAssignment) extends DIException(message, null)
