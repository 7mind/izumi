package izumi.distage.model.exceptions.interpretation

import izumi.distage.model.exceptions.DIException

class ProvisioningException(message: String, cause: Throwable) extends DIException(message, cause)
