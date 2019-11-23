package izumi.distage.model.reflection.universe

import izumi.fundamentals.reflection.UniverseGeneric

trait DIUniverseBase extends UniverseGeneric {
  this: WithDISafeType =>

  type TypeNative = u.Type
  type Symb = u.Symbol
  type MethodSymb = u.MethodSymbol
}
