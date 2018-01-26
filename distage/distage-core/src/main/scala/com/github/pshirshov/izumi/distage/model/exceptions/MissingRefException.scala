package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.DIKey
import com.github.pshirshov.izumi.distage.model.plan.RefTable

class MissingRefException(message: String, val missing: Set[DIKey], val reftable: Option[RefTable]) extends DIException(message, null)
