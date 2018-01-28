package com.github.pshirshov.izumi.fundamentals

import scala.reflect.api.Universe
import scala.reflect.runtime.universe

package object reflection {
  type TypeFull = EqualitySafeType

  type SingletonUniverse = Universe with scala.Singleton
  type Tag[T] = universe.TypeTag[T]
  type TypeNative = universe.Type
  type TypeSymb = universe.Symbol
  type MethodSymb = universe.MethodSymbol
}
