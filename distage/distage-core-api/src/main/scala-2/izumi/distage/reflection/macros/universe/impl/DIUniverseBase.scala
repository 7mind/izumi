package izumi.distage.reflection.macros.universe.impl

import izumi.distage.reflection.macros.universe.ReflectionProvider

import scala.reflect.api.Universe
import scala.reflect.macros.blackbox

trait DIUniverseBase {
  val ctx: blackbox.Context
  val u: Universe
  val rp: ReflectionProvider

  type TypeNative = u.Type
  type SymbNative = u.Symbol
  type MethodSymbNative = u.MethodSymbol
}
