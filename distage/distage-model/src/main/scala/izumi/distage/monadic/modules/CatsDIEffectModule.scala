package izumi.distage.monadic.modules

import cats.effect.IO
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.monadic.{DIEffect, DIEffectRunner}

trait CatsDIEffectModule extends ModuleDef {
  addImplicit[DIEffectRunner[IO]]
  addImplicit[DIEffect[IO]]
}
