package com.github.pshirshov.izumi.idealingua.model.exceptions

class IDLException(message: String, cause: Option[Throwable] = None) extends RuntimeException(message, cause.orNull)
