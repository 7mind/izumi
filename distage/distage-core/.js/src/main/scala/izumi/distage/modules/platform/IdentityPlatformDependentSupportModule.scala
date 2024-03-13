package izumi.distage.modules.platform

import izumi.distage.model.definition.ModuleDef

// Platform-dependent because QuasiAsync[Identity] doesn't work on Scala.js
object IdentityPlatformDependentSupportModule extends ModuleDef
