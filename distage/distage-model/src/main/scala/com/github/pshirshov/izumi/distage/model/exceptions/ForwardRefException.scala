package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.RefTable

class ForwardRefException(message: String, val reftable: RefTable) extends DIException(message, null)


