package izumi.distage.provisioning.strategies.cglib.exceptions

import izumi.distage.model.exceptions.DIException
import izumi.distage.model.reflection.universe.RuntimeDIUniverse

class CgLibCallException(message: String, val arguments: Seq[AnyRef], val expectedArgTypes: Seq[RuntimeDIUniverse.DIKey]) extends DIException(message, null)
