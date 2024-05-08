package izumi.distage.reflection.macros.universe.impl

import izumi.distage.reflection.macros.universe.ReflectionProvider

import scala.reflect.api.Universe

trait DIUniverseBase {
  val u: Universe
  val rp: ReflectionProvider

  type TypeNative = u.Type
  type SymbNative = u.Symbol
  type MethodSymbNative = u.MethodSymbol
}
