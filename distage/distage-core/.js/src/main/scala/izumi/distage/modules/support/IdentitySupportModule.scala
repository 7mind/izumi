package izumi.distage.modules.support

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.effect.{DIApplicative, DIEffect, DIEffectRunner}
import izumi.fundamentals.platform.functional.Identity

object IdentitySupportModule extends IdentitySupportModule

/** `Identity` effect type (aka no effect type / imperative Scala) support for `distage` resources, effects, roles & tests
  *
  * - Adds [[izumi.distage.model.effect.DIEffect]] instances to support running without an effect type in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  */
trait IdentitySupportModule extends ModuleDef {
  addImplicit[DIApplicative[Identity]]
  addImplicit[DIEffect[Identity]]
  addImplicit[DIEffectRunner[Identity]]
//  addImplicit[DIEffectAsync[Identity]] // No DIEffectAsync for Identity on JS
}
