package izumi.distage.model.exceptions

import izumi.distage.model.definition.ImplDef

class UnsupportedDefinitionException(message: String, val definition: ImplDef) extends DIException(message, null)
