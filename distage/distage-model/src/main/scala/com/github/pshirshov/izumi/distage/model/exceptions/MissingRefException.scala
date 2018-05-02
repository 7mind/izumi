package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.references.RefTable
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class MissingRefException(message: String, val missing: Set[RuntimeDIUniverse.DIKey], val reftable: Option[RefTable]) extends DIException(message, null)
