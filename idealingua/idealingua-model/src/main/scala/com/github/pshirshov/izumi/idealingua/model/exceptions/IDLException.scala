package com.github.pshirshov.izumi.idealingua.model.exceptions

import com.github.pshirshov.izumi.idealingua.model.common.TypeId

class IDLException(message: String, cause: Option[Throwable] = None) extends RuntimeException(message, cause.orNull)

case class IDLCyclicInheritanceException(message: String, members: Set[TypeId]) extends IDLException(message)
