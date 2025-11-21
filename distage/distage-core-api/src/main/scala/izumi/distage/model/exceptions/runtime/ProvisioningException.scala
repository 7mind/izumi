package izumi.distage.model.exceptions.runtime

import izumi.distage.model.exceptions.DIException

class ProvisioningException(message: String, captureStackTrace: Boolean = true) extends DIException(message, null, captureStackTrace)
