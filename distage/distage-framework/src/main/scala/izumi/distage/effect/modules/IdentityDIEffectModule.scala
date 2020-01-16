package izumi.distage.effect.modules

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.effect.{DIEffect, DIEffectAsync, DIEffectRunner}
import izumi.fundamentals.platform.functional.Identity

object IdentityDIEffectModule extends IdentityDIEffectModule

trait IdentityDIEffectModule extends ModuleDef {
  addImplicit[DIEffect[Identity]]
  addImplicit[DIEffectRunner[Identity]]
  addImplicit[DIEffectAsync[Identity]]
}
