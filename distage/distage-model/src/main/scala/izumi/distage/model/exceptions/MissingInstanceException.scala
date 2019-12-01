package izumi.distage.model.exceptions

import izumi.distage.model.reflection.universe.RuntimeDIUniverse

class MissingInstanceException(message: String, val key: RuntimeDIUniverse.DIKey) extends DIException(message)



