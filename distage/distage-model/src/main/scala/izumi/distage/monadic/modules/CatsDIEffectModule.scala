package com.github.pshirshov.izumi.distage.monadic.modules

import cats.effect.IO
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.distage.model.monadic.{DIEffect, DIEffectRunner}

trait CatsDIEffectModule extends ModuleDef {
  addImplicit[DIEffectRunner[IO]]
  addImplicit[DIEffect[IO]]
}
