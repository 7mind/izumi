package com.github.pshirshov.izumi.distage.model.reflection.universe

import scala.reflect.api.Universe

trait DIUniverseBase {
  this: WithDISafeType =>

  val u: Universe

  type TypeNative = u.Type
  type Symb = u.Symbol
  type MethodSymb = u.MethodSymbol

}
