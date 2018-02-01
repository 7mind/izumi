package com.github.pshirshov.izumi.idealingua.model

class IDLException(message: String, cause: Option[Throwable] = None) extends RuntimeException(message, cause.orNull)
