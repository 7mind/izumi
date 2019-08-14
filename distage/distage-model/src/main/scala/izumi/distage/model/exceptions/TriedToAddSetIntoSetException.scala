package izumi.distage.model.exceptions

import izumi.distage.model.provisioning.NewObjectOp
import izumi.distage.model.reflection.universe.RuntimeDIUniverse

class TriedToAddSetIntoSetException(message: String, val target: RuntimeDIUniverse.DIKey, val result: NewObjectOp) extends DIException(message, null)
