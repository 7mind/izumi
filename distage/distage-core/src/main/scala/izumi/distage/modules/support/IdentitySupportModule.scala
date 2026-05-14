package izumi.distage.modules.support

import izumi.distage.model.definition.ModuleDef
import izumi.functional.bio.{Clock1, Entropy1}
import izumi.functional.bio.*
import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.TagK

object IdentitySupportModule extends IdentitySupportModule

/**
  * `Identity` effect type (aka no effect type / imperative Scala) support for `distage` resources, effects, roles & tests
  *
  * Adds [[izumi.functional.bio.IO1]] instances to support running without an effect type in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  */
trait IdentitySupportModule extends ModuleDef {
  addImplicit[TagK[Identity]]

  addImplicit[Functor1[Identity]]
  addImplicit[Applicative1[Identity]]
  addImplicit[Primitives1[Identity]]
  addImplicit[IO1[Identity]]
  addImplicit[Async1[Identity]]
  addImplicit[Temporal1[Identity]]
  addImplicit[IORunner1[Identity]]
  make[Clock1[Identity]].fromValue(Clock1.Standard)
  make[Entropy1[Identity]].fromValue(Entropy1.Standard)
}
