package izumi.distage.monadic.modules

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.monadic.{DIEffect, DIEffectAsync, DIEffectRunner}
import izumi.fundamentals.platform.functional.Identity

object IdentityDIEffectModule extends ModuleDef {
  addImplicit[DIEffect[Identity]]
  addImplicit[DIEffectRunner[Identity]]
  addImplicit[DIEffectAsync[Identity]]
}
