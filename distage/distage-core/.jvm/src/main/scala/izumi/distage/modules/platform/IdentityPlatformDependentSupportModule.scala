package izumi.distage.modules.platform

import izumi.distage.model.definition.ModuleDef
import izumi.functional.quasi.QuasiAsync
import izumi.fundamentals.platform.functional.Identity

object IdentityPlatformDependentSupportModule extends ModuleDef {
  addImplicit[QuasiAsync[Identity]]
}
