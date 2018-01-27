package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.definition.ImplDef

class UnsupportedDefinitionException(message: String, val definition: ImplDef) extends DIException(message, null)



