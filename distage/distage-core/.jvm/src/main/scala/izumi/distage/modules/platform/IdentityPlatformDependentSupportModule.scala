package izumi.distage.modules.platform

import izumi.distage.model.definition.ModuleDef
import izumi.functional.quasi.QuasiAsync
import izumi.fundamentals.platform.functional.Identity

// Platform-dependent because QuasiAsync[Identity] doesn't work on Scala.js
object IdentityPlatformDependentSupportModule extends ModuleDef {
  addImplicit[QuasiAsync[Identity]]
}
