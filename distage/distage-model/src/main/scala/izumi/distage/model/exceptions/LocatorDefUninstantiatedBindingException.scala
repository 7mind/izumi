package izumi.distage.model.exceptions

import izumi.distage.model.definition.Binding

class LocatorDefUninstantiatedBindingException(message: String, val binding: Binding) extends DIException(message, null)
