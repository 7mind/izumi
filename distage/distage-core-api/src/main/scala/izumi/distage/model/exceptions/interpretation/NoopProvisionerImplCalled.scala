package izumi.distage.model.exceptions.interpretation

import izumi.distage.model.exceptions.DIException

@deprecated("Needs to be removed", "20/10/2021")
class NoopProvisionerImplCalled(message: String, val instance: AnyRef) extends DIException(message)
