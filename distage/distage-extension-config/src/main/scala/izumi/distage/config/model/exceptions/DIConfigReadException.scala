package izumi.distage.config.model.exceptions

import izumi.distage.model.exceptions.DIException

class DIConfigReadException(message: String, cause: Throwable) extends DIException(message, cause)
