package izumi.distage.model.reflection.universe

import scala.reflect.api.Universe

trait DIUniverseBase {
  this: WithDISafeType =>
  val u: Universe

  type TypeNative = u.Type
  type SymbNative = u.Symbol
  type MethodSymbNative = u.MethodSymbol
}
