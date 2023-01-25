package izumi.distage.modules.support

import izumi.distage.model.definition.ModuleDef
import izumi.functional.quasi.*
import izumi.functional.mono.{Clock, Entropy}
import izumi.fundamentals.platform.functional.Identity

object IdentitySupportModule extends IdentitySupportModule

/** `Identity` effect type (aka no effect type / imperative Scala) support for `distage` resources, effects, roles & tests
  *
  * Adds [[izumi.functional.quasi.QuasiIO]] instances to support running without an effect type in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  */
trait IdentitySupportModule extends ModuleDef {
  addImplicit[QuasiFunctor[Identity]]
  addImplicit[QuasiApplicative[Identity]]
  addImplicit[QuasiPrimitives[Identity]]
  addImplicit[QuasiIO[Identity]]
  addImplicit[QuasiIORunner[Identity]]
//  addImplicit[QuasiAsync[Identity]] // No QuasiAsync for Identity on JS
  make[Clock[Identity]].fromValue(Clock.Standard)
  make[Entropy[Identity]].fromValue(Entropy.Standard)
}
