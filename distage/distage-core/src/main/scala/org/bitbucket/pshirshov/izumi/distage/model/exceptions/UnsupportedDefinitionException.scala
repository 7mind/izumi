package org.bitbucket.pshirshov.izumi.distage.model.exceptions

import org.bitbucket.pshirshov.izumi.distage.definition.ImplDef

class UnsupportedDefinitionException(message: String, val definition: ImplDef) extends DIException(message, null)



