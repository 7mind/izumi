package izumi.distage.model.exceptions

import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Provider

// TODO remove FactoryFunction ???
@deprecated("remove factory providers", "???")
class FactoryProvidersCannotBeCombined(message: String, val provider1: Provider.FactoryProvider, val provider2: Provider.FactoryProvider) extends DIException(message)
