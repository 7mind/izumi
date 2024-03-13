package izumi.distage.model.exceptions.dsl

import izumi.distage.model.definition.Binding
import izumi.distage.model.exceptions.DIException

class LocatorDefUninstantiatedBindingException(message: String, val binding: Binding) extends DIException(message)
