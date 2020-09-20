package izumi.distage.effect.modules

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.effect.{DIApplicative, DIEffect, DIEffectAsync, DIEffectRunner}
import izumi.fundamentals.platform.functional.Identity

object IdentityDIEffectModule extends IdentityDIEffectModule

/** `Identity` effect type (aka no effect type / imperative Scala) support for `distage` resources, effects, roles & tests */
trait IdentityDIEffectModule extends ModuleDef {
  addImplicit[DIApplicative[Identity]]
  addImplicit[DIEffect[Identity]]
  addImplicit[DIEffectRunner[Identity]]
  addImplicit[DIEffectAsync[Identity]]
}
