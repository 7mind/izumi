package izumi.distage.effect.modules

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.effect.{DIApplicative, DIEffect, DIEffectAsync, DIEffectRunner}
import izumi.fundamentals.platform.functional.Identity

@deprecated("Redundant inclusion. Now included by default when using distage-framework/testkit or any of Injector.produceF/planF methods", "0.11")
object IdentityDIEffectModule extends IdentityDIEffectModule

/** `Identity` effect type (aka no effect type / imperative Scala) support for `distage` resources, effects, roles & tests
  *
  * - Adds [[izumi.distage.model.effect.DIEffect]] instances to support running without an effect type in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  */
@deprecatedInheritance("Redundant inclusion. Now included by default when using distage-framework/testkit or any of Injector.produceF/planF methods", "0.11")
trait IdentityDIEffectModule extends ModuleDef {
  addImplicit[DIApplicative[Identity]]
  addImplicit[DIEffect[Identity]]
  addImplicit[DIEffectRunner[Identity]]
  addImplicit[DIEffectAsync[Identity]]
}
