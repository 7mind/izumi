package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.definition.Binding

class LocatorDefUninstantiatedBindingException(message: String, val binding: Binding) extends DIException(message, null)
