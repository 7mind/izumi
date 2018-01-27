package com.github.pshirshov.izumi.distage.model.exceptions

class ForwardRefException(message: String, val reftable: RefTable) extends DIException(message, null)


