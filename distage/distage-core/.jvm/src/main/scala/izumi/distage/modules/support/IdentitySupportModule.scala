package izumi.distage.modules.support

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.effect.{QuasiApplicative, QuasiAsync, QuasiEffect, QuasiEffectRunner}
import izumi.fundamentals.platform.functional.Identity

object IdentitySupportModule extends IdentitySupportModule

/** `Identity` effect type (aka no effect type / imperative Scala) support for `distage` resources, effects, roles & tests
  *
  * - Adds [[izumi.distage.model.effect.QuasiEffect]] instances to support running without an effect type in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  */
trait IdentitySupportModule extends ModuleDef {
  addImplicit[QuasiApplicative[Identity]]
  addImplicit[QuasiEffect[Identity]]
  addImplicit[QuasiEffectRunner[Identity]]
  addImplicit[QuasiAsync[Identity]]
}
