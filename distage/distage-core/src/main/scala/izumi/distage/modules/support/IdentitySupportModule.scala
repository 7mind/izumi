package izumi.distage.modules.support

import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.platform.IdentityPlatformDependentSupportModule
import izumi.functional.bio.{Clock1, Entropy1}
import izumi.functional.quasi.*
import izumi.fundamentals.platform.functional.Identity

object IdentitySupportModule extends IdentitySupportModule

/**
  * `Identity` effect type (aka no effect type / imperative Scala) support for `distage` resources, effects, roles & tests
  *
  * Adds [[izumi.functional.quasi.QuasiIO]] instances to support running without an effect type in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  */
trait IdentitySupportModule extends ModuleDef {
  include(IdentityPlatformDependentSupportModule)
  addImplicit[QuasiFunctor[Identity]]
  addImplicit[QuasiApplicative[Identity]]
  addImplicit[QuasiPrimitives[Identity]]
  addImplicit[QuasiIO[Identity]]
  addImplicit[QuasiTemporal[Identity]]
  addImplicit[QuasiIORunner[Identity]]
  make[Clock1[Identity]].fromValue(Clock1.Standard)
  make[Entropy1[Identity]].fromValue(Entropy1.Standard)
}
