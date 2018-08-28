package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.fundamentals.reflection.UniverseGeneric

trait DIUniverseBase extends UniverseGeneric {
  this: WithDISafeType =>

  type TypeNative = u.Type
  type Symb = u.Symbol
  type MethodSymb = u.MethodSymbol

}
