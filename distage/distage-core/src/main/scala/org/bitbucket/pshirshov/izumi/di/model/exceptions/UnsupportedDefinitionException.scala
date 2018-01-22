package org.bitbucket.pshirshov.izumi.di.model.exceptions

import org.bitbucket.pshirshov.izumi.di.TypeFull
import org.bitbucket.pshirshov.izumi.di.definition.ImplDef

class UnsupportedDefinitionException(message: String, val definition: ImplDef) extends DIException(message, null)
class UnsupportedWiringException(message: String, val tpe: TypeFull) extends DIException(message, null)


