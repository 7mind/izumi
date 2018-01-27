package com.github.pshirshov.izumi.fundamentals

import scala.reflect.runtime.universe
import scala.reflect.runtime.universe._

package object reflection {
  type Tag[T] = TypeTag[T]
  type TypeFull = EqualitySafeType
  type TypeNative = universe.Type
  type TypeSymb = universe.Symbol
  type MethodSymb = universe.MethodSymbol
}
