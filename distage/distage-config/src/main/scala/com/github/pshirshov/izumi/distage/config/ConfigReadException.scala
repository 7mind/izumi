package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.izumi.distage.model.exceptions.DIException

class ConfigReadException(message: String, cause: Throwable = null) extends DIException(message, cause)
